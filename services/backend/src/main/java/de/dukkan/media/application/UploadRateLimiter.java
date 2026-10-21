package de.dukkan.media.application;

import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed one-hour window per user for uploads ({@code dukkan.media.uploads-per-hour}, default 60),
 * per instance like {@code shared.web.AuthRateLimitFilter}. It runs after authentication, which a
 * servlet filter before Spring Security cannot do, so it lives in the service. Every attempt
 * counts, including rejected files.
 */
@Component
public class UploadRateLimiter {

  private static final Duration WINDOW = Duration.ofHours(1);
  private static final int MAX_TRACKED_USERS = 50_000;

  private record Window(long startEpochSecond, int count) {}

  private final int limit;
  private final Clock clock;
  private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();

  public UploadRateLimiter(DukkanProperties properties, Clock clock) {
    this.limit = properties.media().uploadsPerHour();
    this.clock = clock;
  }

  /** Counts one upload for the user, or throws {@link RateLimitedException} when the window is full. */
  public void acquire(UUID userId) {
    long now = clock.instant().getEpochSecond();
    long window = WINDOW.toSeconds();
    if (windows.size() > MAX_TRACKED_USERS) {
      windows.entrySet().removeIf(e -> now - e.getValue().startEpochSecond() >= window);
    }
    Window current =
        windows.compute(
            userId,
            (k, w) ->
                w == null || now - w.startEpochSecond() >= window
                    ? new Window(now, 1)
                    : new Window(w.startEpochSecond(), w.count() + 1));
    if (current.count() > limit) {
      long retryAfter = current.startEpochSecond() + window - now;
      throw new RateLimitedException(retryAfter, "Too many uploads. Try again in " + Math.max(1, retryAfter) + " seconds.");
    }
  }
}
