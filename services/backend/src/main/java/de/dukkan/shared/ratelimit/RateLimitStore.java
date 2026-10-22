package de.dukkan.shared.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Where rate-limit counters live: shared by all instances in production (the database). */
public interface RateLimitStore {

  /** Counts one attempt in the window and returns the attempts so far, including this one. */
  int increment(String bucket, String key, Instant windowStart, Instant expiresAt);

  /** A store for one process, e.g. in unit tests. */
  static RateLimitStore inMemory() {
    ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
    return (bucket, key, windowStart, expiresAt) -> counts.merge(bucket + '|' + key + '|' + windowStart.getEpochSecond(), 1, Integer::sum);
  }
}
