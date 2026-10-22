package de.dukkan.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dukkan.shared.error.RateLimitedException;
import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** B-53: limits hold across instances and count attempts whose request rolls back. */
class SharedRateLimitIntegrationTest extends IntegrationTest {

  @Autowired private RateLimitStore store;
  @Autowired private PlatformTransactionManager transactions;

  @Test
  void twoInstancesShareOneCounter() {
    // Two limiter objects stand for two replicas: together they allow 3 attempts per hour.
    FixedWindowRateLimiter<UUID> first = new FixedWindowRateLimiter<>("test-shared", 3, Duration.ofHours(1), clock, store);
    FixedWindowRateLimiter<UUID> second = new FixedWindowRateLimiter<>("test-shared", 3, Duration.ofHours(1), clock, store);
    UUID user = UUID.randomUUID();
    first.acquire(user, "tests");
    second.acquire(user, "tests");
    first.acquire(user, "tests");
    assertThatThrownBy(() -> second.acquire(user, "tests"))
        .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 3600L));
    // Another bucket and another key are independent.
    assertThatCode(() -> new FixedWindowRateLimiter<UUID>("test-other", 3, Duration.ofHours(1), clock, store).acquire(user, "tests"))
        .doesNotThrowAnyException();
    assertThatCode(() -> first.acquire(UUID.randomUUID(), "tests")).doesNotThrowAnyException();
    // The next window starts fresh.
    clock.advance(Duration.ofHours(1));
    assertThatCode(() -> first.acquire(user, "tests")).doesNotThrowAnyException();
  }

  @Test
  void anAttemptCountsEvenWhenItsTransactionRollsBack() {
    FixedWindowRateLimiter<UUID> limiter = new FixedWindowRateLimiter<>("test-rollback", 2, Duration.ofHours(1), clock, store);
    UUID user = UUID.randomUUID();
    TransactionTemplate request = new TransactionTemplate(transactions);
    for (int i = 0; i < 2; i++) {
      request.executeWithoutResult(
          status -> {
            limiter.acquire(user, "tests");
            status.setRollbackOnly(); // e.g. a wrong password
          });
    }
    assertThatThrownBy(() -> request.executeWithoutResult(status -> limiter.acquire(user, "tests"))).isInstanceOf(RateLimitedException.class);
  }
}
