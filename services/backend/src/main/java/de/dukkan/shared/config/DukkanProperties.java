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
    @DefaultValue Features features,
    @DefaultValue Fulfilment fulfilment,
    @DefaultValue Catalog catalog,
    @DefaultValue Markets markets,
    @DefaultValue Security security) {

  /** Feature flags. */
  public record Features(
      @DefaultValue("false") boolean requireLoginToBrowse, @DefaultValue("true") boolean merchantAutoApprove) {}

  /** Slot generation and capacity rules (business rules in packages/shared-contracts/README.md). */
  public record Fulfilment(
      @DefaultValue("7") int daysAhead,
      @DefaultValue("PT1H") Duration slotLength,
      @DefaultValue("6") int deliveryCapacity,
      @DefaultValue("10") int pickupCapacity,
      @DefaultValue("PT2H") Duration deliveryCutoff,
      @DefaultValue("PT1H") Duration pickupCutoff) {
    public Fulfilment {
      if (daysAhead < 1 || daysAhead > 31) {
        throw new IllegalArgumentException("dukkan.fulfilment.days-ahead must be within 1..31");
      }
      if (slotLength.isNegative() || slotLength.isZero() || slotLength.compareTo(Duration.ofHours(12)) > 0) {
        throw new IllegalArgumentException("dukkan.fulfilment.slot-length must be within (0, 12h]");
      }
      if (deliveryCapacity < 1 || pickupCapacity < 1) {
        throw new IllegalArgumentException("dukkan.fulfilment capacities must be >= 1");
      }
      if (deliveryCutoff.isNegative() || pickupCutoff.isNegative()) {
        throw new IllegalArgumentException("dukkan.fulfilment cutoffs must not be negative");
      }
    }
  }

  /** Catalog rules. */
  public record Catalog(@DefaultValue("14") int newListingDays, @DefaultValue("24") int defaultMaxOrderQuantity) {
    public Catalog {
      if (newListingDays < 0) {
        throw new IllegalArgumentException("dukkan.catalog.new-listing-days must be >= 0");
      }
      if (defaultMaxOrderQuantity < 1 || defaultMaxOrderQuantity > 99) {
        throw new IllegalArgumentException("dukkan.catalog.default-max-order-quantity must be within 1..99");
      }
    }
  }

  /** Market discovery rules. */
  public record Markets(@DefaultValue("10") double defaultRadiusKm, @DefaultValue("50") double maxRadiusKm) {
    public Markets {
      if (defaultRadiusKm <= 0 || maxRadiusKm < defaultRadiusKm) {
        throw new IllegalArgumentException("dukkan.markets radii must satisfy 0 < default <= max");
      }
    }
  }

  /** Security settings. */
  public record Security(@DefaultValue Jwt jwt, @DefaultValue RateLimit rateLimit) {}

  /**
   * Per client IP, per instance limits for the unauthenticated auth endpoints (brute force and
   * throwaway accounts). Behind a proxy, enable {@code server.forward-headers-strategy} so that the
   * client IP is the real one.
   */
  public record RateLimit(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("20") int loginsPerMinute,
      @DefaultValue("10") int registrationsPerHour) {
    public RateLimit {
      if (loginsPerMinute < 1 || registrationsPerHour < 1) {
        throw new IllegalArgumentException("dukkan.security.rate-limit values must be >= 1");
      }
    }
  }

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
