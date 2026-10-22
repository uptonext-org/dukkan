package de.dukkan.media.application;

import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.RateLimitedException;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fixed one-hour window per user for uploads ({@code dukkan.media.uploads-per-hour}, default 60),
 * shared across instances like {@code shared.web.AuthRateLimitFilter}. It runs after authentication, which a
 * servlet filter before Spring Security cannot do, so it lives in the service. Every attempt
 * counts, including rejected files.
 */
@Component
public class UploadRateLimiter {

  private final FixedWindowRateLimiter<UUID> limiter;

  public UploadRateLimiter(DukkanProperties properties, Clock clock, RateLimitStore store) {
    this.limiter = new FixedWindowRateLimiter<>("uploads", properties.media().uploadsPerHour(), Duration.ofHours(1), clock, store);
  }

  /** Counts one upload for the user, or throws {@link RateLimitedException} when the window is full. */
  public void acquire(UUID userId) {
    limiter.acquire(userId, "uploads");
  }
}
