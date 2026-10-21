package de.dukkan.shared.web;

import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client IP for {@code POST /api/v1/auth/login} and {@code /register}.
 * Answers 429 problem+json with code {@code RATE_LIMITED} and {@code Retry-After}. Limits are per
 * instance (no shared store yet); good enough against scripted abuse of a single replica.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REGISTER = "/api/v1/auth/register";
  private static final int MAX_TRACKED_KEYS = 50_000;

  private record Window(long startEpochSecond, int count) {}

  private final DukkanProperties.RateLimit limits;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public AuthRateLimitFilter(DukkanProperties properties, Clock clock) {
    this.limits = properties.security().rateLimit();
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !limits.enabled() || !"POST".equals(request.getMethod()) || !(LOGIN.equals(path) || REGISTER.equals(path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean login = LOGIN.equals(request.getRequestURI());
    Duration window = login ? Duration.ofMinutes(1) : Duration.ofHours(1);
    int limit = login ? limits.loginsPerMinute() : limits.registrationsPerHour();
    long now = clock.instant().getEpochSecond();
    String key = (login ? "login|" : "register|") + request.getRemoteAddr();

    if (windows.size() > MAX_TRACKED_KEYS) {
      windows.entrySet().removeIf(e -> now - e.getValue().startEpochSecond() > Duration.ofHours(1).toSeconds());
    }
    Window current =
        windows.compute(
            key,
            (k, w) ->
                w == null || now - w.startEpochSecond() >= window.toSeconds()
                    ? new Window(now, 1)
                    : new Window(w.startEpochSecond(), w.count() + 1));
    if (current.count() > limit) {
      long retryAfter = Math.max(1, current.startEpochSecond() + window.toSeconds() - now);
      response.setStatus(429);
      response.setHeader("Retry-After", Long.toString(retryAfter));
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response
          .getWriter()
          .write(
              "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"code\":\""
                  + ErrorCodes.RATE_LIMITED
                  + "\",\"detail\":\"Too many attempts. Try again in "
                  + retryAfter
                  + " seconds.\",\"instance\":\""
                  + request.getRequestURI()
                  + "\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
