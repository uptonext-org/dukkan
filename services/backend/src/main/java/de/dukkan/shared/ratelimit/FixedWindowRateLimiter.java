package de.dukkan.shared.ratelimit;

import de.dukkan.shared.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window counter per key (windows aligned to the epoch, e.g. full hours), stored in a {@link
 * RateLimitStore} shared by all instances (B-53).
 *
 * @param <K> the key type, e.g. a user id
 */
public final class FixedWindowRateLimiter<K> {

  private final String bucket;
  private final int limit;
  private final Duration window;
  private final Clock clock;
  private final RateLimitStore store;

  /** {@code bucket} names the limit in the store, e.g. {@code uploads}. */
  public FixedWindowRateLimiter(String bucket, int limit, Duration window, Clock clock, RateLimitStore store) {
    if (limit < 1 || window.isNegative() || window.isZero() || window.toSeconds() < 1) {
      throw new IllegalArgumentException("limit must be >= 1 and window at least one second");
    }
    this.bucket = bucket;
    this.limit = limit;
    this.window = window;
    this.clock = clock;
    this.store = store;
  }

  /**
   * Counts one attempt for the key, or throws {@link RateLimitedException} once the window is full.
   * {@code what} is a plural noun for the message, e.g. {@code uploads}.
   */
  public void acquire(K key, String what) {
    long now = clock.instant().getEpochSecond();
    long length = window.toSeconds();
    long start = now - Math.floorMod(now, length);
    int count = store.increment(bucket, String.valueOf(key), Instant.ofEpochSecond(start), Instant.ofEpochSecond(start + length));
    if (count > limit) {
      long retryAfter = Math.max(1, start + length - now);
      throw new RateLimitedException(retryAfter, "Too many " + what + ". Try again in " + retryAfter + " seconds.");
    }
  }
}
