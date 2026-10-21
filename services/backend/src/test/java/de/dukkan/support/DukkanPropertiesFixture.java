package de.dukkan.support;

import de.dukkan.shared.config.DukkanProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.util.unit.DataSize;

/** Default settings for unit tests that build {@link DukkanProperties} without Spring. */
public final class DukkanPropertiesFixture {

  public static final String SECRET = "unit-test-secret-0123456789abcdef-xyz";

  private DukkanPropertiesFixture() {}

  public static DukkanProperties defaults() {
    return build(new DukkanProperties.Jwt(SECRET, Duration.ofHours(1), "dukkan"), new DukkanProperties.Fcm(null, null, "https://fcm.googleapis.com", null));
  }

  public static DukkanProperties withJwt(String secret, Duration ttl) {
    return build(new DukkanProperties.Jwt(secret, ttl, "dukkan"), new DukkanProperties.Fcm(null, null, "https://fcm.googleapis.com", null));
  }

  public static DukkanProperties withFcm(DukkanProperties.Fcm fcm) {
    return build(new DukkanProperties.Jwt(SECRET, Duration.ofHours(1), "dukkan"), fcm);
  }

  private static DukkanProperties build(DukkanProperties.Jwt jwt, DukkanProperties.Fcm fcm) {
    return new DukkanProperties(
        new DukkanProperties.Features(false, true),
        new DukkanProperties.Fulfilment(7, Duration.ofHours(1), 6, 10, Duration.ofHours(2), Duration.ofHours(1)),
        new DukkanProperties.Catalog(14, 24),
        new DukkanProperties.Markets(10, 50),
        new DukkanProperties.Security(
            jwt,
            new DukkanProperties.RateLimit(true, 20, 10, 60, 5),
            new DukkanProperties.Auth(
                Duration.ofDays(30),
                Duration.ofMinutes(30),
                "dukkan://reset-password",
                "Dukkan <no-reply@dukkan.app>",
                false,
                new DukkanProperties.Provider(List.of(), List.of(), null),
                new DukkanProperties.Provider(List.of(), List.of(), null))),
        new DukkanProperties.Media(
            null, null, DataSize.ofMegabytes(5), 40_000_000, 60, "filesystem", new DukkanProperties.S3(null, "eu-central-1", null, null, null, false, false)),
        new DukkanProperties.Capture(30),
        new DukkanProperties.Push(fcm),
        new DukkanProperties.Ai(new DukkanProperties.Anthropic(null, "claude-opus-5-5", "low", Duration.ofSeconds(60), 8000, true, null)),
        new DukkanProperties.Translation(60));
  }
}
