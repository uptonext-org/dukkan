package de.dukkan.shared.ratelimit;

import de.dukkan.shared.error.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counter per key, held in memory per instance (a shared store is backlog B-39).
 * Stale windows are swept once more than {@code maxTrackedKeys} keys are tracked.
 *
 * @param <K> the key type, e.g. a user id
 */
public final class FixedWindowRateLimiter<K> {

  private static final int MAX_TRACKED_KEYS = 50_000;

  private record Window(long startEpochSecond, int count) {}

  private final int limit;
  private final Duration window;
  private final Clock clock;
  private final ConcurrentHashMap<K, Window> windows = new ConcurrentHashMap<>();

  public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
    if (limit < 1 || window.isNegative() || window.isZero()) {
      throw new IllegalArgumentException("limit must be >= 1 and window positive");
    }
    this.limit = limit;
    this.window = window;
    this.clock = clock;
  }

  /**
   * Counts one attempt for the key, or throws {@link RateLimitedException} once the window is full.
   *
   * @param what plural noun for the message, e.g. {@code uploads}
   */
  public void acquire(K key, String what) {
    long now = clock.instant().getEpochSecond();
    long length = window.toSeconds();
    if (windows.size() > MAX_TRACKED_KEYS) {
      windows.entrySet().removeIf(e -> now - e.getValue().startEpochSecond() >= length);
    }
    Window current =
        windows.compute(
            key,
            (k, w) ->
                w == null || now - w.startEpochSecond() >= length
                    ? new Window(now, 1)
                    : new Window(w.startEpochSecond(), w.count() + 1));
    if (current.count() > limit) {
      long retryAfter = current.startEpochSecond() + length - now;
      throw new RateLimitedException(retryAfter, "Too many " + what + ". Try again in " + Math.max(1, retryAfter) + " seconds.");
    }
  }
}
