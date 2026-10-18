package de.dukkan.shared.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All application settings under {@code dukkan.*}. Validation happens in the compact constructors
 * so that an invalid configuration fails at startup.
 */
@ConfigurationProperties("dukkan")
public record DukkanProperties(
    @DefaultValue Checkout checkout, @DefaultValue Delivery delivery, @DefaultValue Security security) {

  /** Checkout rules. */
  public record Checkout(@DefaultValue("3500") long minimumOrderCents) {
    public Checkout {
      if (minimumOrderCents < 0) {
        throw new IllegalArgumentException("dukkan.checkout.minimum-order-cents must be >= 0");
      }
    }
  }

  /** Delivery slot generation rules. */
  public record Delivery(
      @DefaultValue("8") int slotCapacity,
      @DefaultValue("22") int cutoffHour,
      @DefaultValue("7") int daysAhead,
      @DefaultValue("8") int firstSlotHour,
      @DefaultValue("20") int lastSlotHour) {
    public Delivery {
      if (slotCapacity < 1) {
        throw new IllegalArgumentException("dukkan.delivery.slot-capacity must be >= 1");
      }
      if (cutoffHour < 0 || cutoffHour > 23) {
        throw new IllegalArgumentException("dukkan.delivery.cutoff-hour must be within 0..23");
      }
      if (daysAhead < 1 || daysAhead > 31) {
        throw new IllegalArgumentException("dukkan.delivery.days-ahead must be within 1..31");
      }
      if (firstSlotHour < 0 || lastSlotHour > 22 || firstSlotHour > lastSlotHour) {
        throw new IllegalArgumentException("dukkan.delivery slot hours must satisfy 0 <= first <= last <= 22");
      }
    }
  }

  /** Security settings. */
  public record Security(@DefaultValue Jwt jwt) {}

  /** Self-issued HS256 JWT settings. The secret must be at least 32 bytes (256 bits). */
  public record Jwt(String secret, @DefaultValue("PT1H") Duration ttl, @DefaultValue("dukkan") String issuer) {

    public static final int MIN_SECRET_BYTES = 32;

    public Jwt {
      if (secret == null || secret.getBytes(UTF_8).length < MIN_SECRET_BYTES) {
        throw new IllegalArgumentException(
            "dukkan.security.jwt.secret (env DUKKAN_JWT_SECRET) must be set and at least "
                + MIN_SECRET_BYTES
                + " bytes long");
      }
      if (ttl.isNegative() || ttl.isZero()) {
        throw new IllegalArgumentException("dukkan.security.jwt.ttl must be positive");
      }
    }

    @Override
    public String toString() {
      return "Jwt[secret=***, ttl=" + ttl + ", issuer=" + issuer + "]";
    }
  }
}
