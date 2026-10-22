package de.dukkan.media.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.RateLimitedException;
import de.dukkan.shared.ratelimit.RateLimitStore;
import de.dukkan.support.MutableClock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;

class UploadRateLimiterTest {

  private static DukkanProperties properties(int perHour) {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            Map.of("dukkan.security.jwt.secret", "x".repeat(40), "dukkan.media.uploads-per-hour", Integer.toString(perHour)));
    return new Binder(source).bindOrCreate("dukkan", DukkanProperties.class);
  }

  @Test
  void allowsTheConfiguredNumberPerUserAndHour() {
    MutableClock clock = new MutableClock();
    UploadRateLimiter limiter = new UploadRateLimiter(properties(3), clock, RateLimitStore.inMemory());
    UUID anna = UUID.randomUUID();
    UUID ben = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      limiter.acquire(anna);
    }
    assertThatThrownBy(() -> limiter.acquire(anna))
        .isInstanceOfSatisfying(
            RateLimitedException.class,
            e -> {
              assertThat(e.code()).isEqualTo("RATE_LIMITED");
              assertThat(e.retryAfterSeconds()).isBetween(1L, 3600L);
            });
    assertThatCode(() -> limiter.acquire(ben)).doesNotThrowAnyException();

    clock.advance(Duration.ofMinutes(61));
    assertThatCode(() -> limiter.acquire(anna)).doesNotThrowAnyException();
  }
}
