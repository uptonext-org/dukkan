package de.dukkan.shared.web;

import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.ratelimit.RateLimitStore;
import de.dukkan.shared.security.SecurityEvents;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Fixed-window rate limit per client IP for the public auth endpoints: login and social sign-in
 * (per minute), registration (per hour), token refresh (per minute) and both password reset steps
 * (per hour).
 * Answers 429 problem+json with code {@code RATE_LIMITED} and {@code Retry-After}. Counters live in
 * the shared {@link RateLimitStore} (B-53), so a limit holds across all replicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final String AUTH = "/api/v1/auth/";

  private record Rule(String name, Duration window, int limit) {}

  private final DukkanProperties.RateLimit limits;
  private final Clock clock;
  private final RateLimitStore store;
  private final CorsConfigurationSource cors;
  private final Map<String, Rule> rules;

  public AuthRateLimitFilter(
      DukkanProperties properties, Clock clock, RateLimitStore store, @Qualifier("corsConfigurationSource") CorsConfigurationSource cors) {
    this.limits = properties.security().rateLimit();
    this.cors = cors;
    this.clock = clock;
    this.store = store;
    Rule login = new Rule("login", Duration.ofMinutes(1), limits.loginsPerMinute());
    Rule reset = new Rule("password-reset", Duration.ofHours(1), limits.passwordResetsPerHour());
    this.rules =
        Map.of(
            AUTH + "login", login,
            AUTH + "social", login,
            AUTH + "register", new Rule("register", Duration.ofHours(1), limits.registrationsPerHour()),
            AUTH + "refresh", new Rule("refresh", Duration.ofMinutes(1), limits.refreshesPerMinute()),
            AUTH + "password-reset", reset,
            AUTH + "password-reset/confirm", new Rule("password-reset-confirm", Duration.ofHours(1), limits.passwordResetsPerHour() * 4),
            AUTH + "verify-email", new Rule("verify-email", Duration.ofHours(1), limits.passwordResetsPerHour() * 4),
            "/api/v1/me/email-verification", new Rule("verification-mail", Duration.ofHours(1), limits.passwordResetsPerHour()));
  }

  /**
   * The path Spring MVC matches the request to: percent-decoded, without {@code ;} parameters and
   * with duplicate slashes removed. Comparing the raw URI would let {@code /auth/%6Cogin} reach the
   * login endpoint without being counted.
   */
  private static String endpoint(HttpServletRequest request) {
    return UrlPathHelper.defaultInstance.getLookupPathForRequest(request);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !limits.enabled() || !"POST".equals(request.getMethod()) || !rules.containsKey(endpoint(request));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Rule rule = rules.get(endpoint(request));
    Duration window = rule.window();
    int limit = rule.limit();
    long now = clock.instant().getEpochSecond();
    long length = window.toSeconds();
    long start = now - Math.floorMod(now, length);
    int count =
        store.increment(
            "auth-" + rule.name(), request.getRemoteAddr(), Instant.ofEpochSecond(start), Instant.ofEpochSecond(start + length));
    if (count > limit) {
      SecurityEvents.rateLimited("auth-" + rule.name(), request.getRemoteAddr(), endpoint(request));
      long retryAfter = Math.max(1, start + length - now);
      // This filter answers before Spring Security's CORS filter: without the CORS headers a web app
      // on another origin sees a network error instead of RATE_LIMITED and Retry-After.
      CorsConfiguration corsConfig = cors.getCorsConfiguration(request);
      if (corsConfig != null) {
        new DefaultCorsProcessor().processRequest(corsConfig, request, response);
      }
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
                  + endpoint(request)
                  + "\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
