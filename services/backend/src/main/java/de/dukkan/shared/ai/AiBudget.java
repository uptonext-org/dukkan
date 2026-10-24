package de.dukkan.shared.ai;

import de.dukkan.shared.error.RateLimitedException;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Platform-wide ceiling on language model calls per hour ({@code dukkan.ai.requests-per-hour}),
 * across all users and instances: the per-user limits cannot stop many accounts together. When it is
 * reached, features fall back to their rule-based paths (or answer that AI is unavailable) until the
 * next hour; the cost stays bounded.
 */
@Component
public class AiBudget {

  private static final Logger LOG = LoggerFactory.getLogger(AiBudget.class);

  private final FixedWindowRateLimiter<String> limiter;

  public AiBudget(@Value("${dukkan.ai.requests-per-hour:500}") int perHour, Clock clock, RateLimitStore store) {
    this.limiter = new FixedWindowRateLimiter<>("ai-global", perHour, Duration.ofHours(1), clock, store);
  }

  /** Counts one model call; false when this hour's budget is used up. */
  public boolean tryAcquire() {
    try {
      limiter.acquire("all", "AI requests");
      return true;
    } catch (RateLimitedException e) {
      LOG.warn("AI budget for this hour used up (dukkan.ai.requests-per-hour); using rule-based fallbacks");
      return false;
    }
  }
}
