package de.dukkan.shared.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

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
    @DefaultValue Security security,
    @DefaultValue Media media,
    @DefaultValue Capture capture,
    @DefaultValue Push push,
    @DefaultValue Ai ai,
    @DefaultValue Translation translation) {

  /** Machine translation of merchant texts (B-38): suggestions per merchant and hour. */
  public record Translation(@DefaultValue("60") int requestsPerHour) {
    public Translation {
      if (requestsPerHour < 1) {
        throw new IllegalArgumentException("dukkan.translation.requests-per-hour must be >= 1");
      }
    }
  }

  /** Language model settings shared by AI capture (ADR 0011) and machine translation (ADR 0013). */
  public record Ai(@DefaultValue Anthropic anthropic) {}

  /** Push notifications (B-37). */
  public record Push(@DefaultValue Fcm fcm) {}

  /**
   * Firebase Cloud Messaging HTTP v1. Enabled when {@code projectId} and {@code credentialsFile} (a
   * service account JSON with the "Firebase Cloud Messaging API" role) are set. {@code apiBaseUrl}
   * and {@code tokenUri} only change in tests.
   */
  public record Fcm(
      @Nullable String projectId,
      @Nullable Path credentialsFile,
      @DefaultValue("https://fcm.googleapis.com") String apiBaseUrl,
      @Nullable String tokenUri) {
    public Fcm {
      projectId = blankToNull(projectId);
      tokenUri = blankToNull(tokenUri);
      if (credentialsFile != null && credentialsFile.toString().isBlank()) {
        credentialsFile = null;
      }
    }

    public boolean enabled() {
      return projectId != null && credentialsFile != null;
    }
  }

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
  public record Security(@DefaultValue Jwt jwt, @DefaultValue RateLimit rateLimit, @DefaultValue Auth auth) {}

  /**
   * Sessions and account recovery (B-39). Refresh tokens rotate on every use and live {@code
   * refreshTokenTtl}; password reset links live {@code passwordResetTtl} and point to {@code
   * passwordResetUrl} with {@code ?token=…} appended (the app's deep link by default). {@code
   * logResetLinks} writes reset links to the log instead of needing SMTP: development only.
   */
  public record Auth(
      @DefaultValue("P30D") Duration refreshTokenTtl,
      @DefaultValue("PT30M") Duration passwordResetTtl,
      @DefaultValue("dukkan://reset-password") String passwordResetUrl,
      @DefaultValue("Dukkan <no-reply@dukkan.app>") String mailFrom,
      @DefaultValue("false") boolean logResetLinks,
      @DefaultValue Provider google,
      @DefaultValue Provider apple) {
    public Auth {
      if (refreshTokenTtl.isNegative() || refreshTokenTtl.isZero() || passwordResetTtl.isNegative() || passwordResetTtl.isZero()) {
        throw new IllegalArgumentException("dukkan.security.auth TTLs must be positive");
      }
    }
  }

  /**
   * An OpenID Connect provider whose ID tokens the app sends ({@code POST /auth/social}). Disabled
   * while {@code clientIds} is empty. Issuers and the JWKS URL default to Google's or Apple's when
   * left empty (see {@code identity.infrastructure.OidcIdTokenVerifier}); tests point them at a stub.
   */
  public record Provider(@DefaultValue List<String> clientIds, @DefaultValue List<String> issuers, @Nullable String jwkSetUri) {
    public Provider {
      clientIds = clientIds.stream().filter(id -> !id.isBlank()).toList();
      issuers = issuers.stream().filter(id -> !id.isBlank()).toList();
      jwkSetUri = blankToNull(jwkSetUri);
    }

    public boolean enabled() {
      return !clientIds.isEmpty();
    }
  }

  /**
   * Per client IP, per instance limits for the unauthenticated auth endpoints (brute force and
   * throwaway accounts). Behind a proxy, enable {@code server.forward-headers-strategy} so that the
   * client IP is the real one.
   */
  public record RateLimit(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("20") int loginsPerMinute,
      @DefaultValue("10") int registrationsPerHour,
      @DefaultValue("60") int refreshesPerMinute,
      @DefaultValue("5") int passwordResetsPerHour) {
    public RateLimit {
      if (loginsPerMinute < 1 || registrationsPerHour < 1 || refreshesPerMinute < 1 || passwordResetsPerHour < 1) {
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

  /**
   * Image uploads (module {@code media}). {@code directory} is where the filesystem storage keeps
   * the files ({@code application.yml}: {@code ${java.io.tmpdir}/dukkan-media}, docker profile: a
   * volume). {@code publicBaseUrl} is the absolute URL prefix under which keys are served, e.g.
   * {@code https://api.dukkan.app/api/v1/media} or a CDN; when empty it is derived from each
   * upload request ({@code <origin>/api/v1/media}). Keep {@code maxFileSize} in line with {@code
   * spring.servlet.multipart.max-file-size}.
   */
  public record Media(
      @Nullable Path directory,
      @Nullable String publicBaseUrl,
      @DefaultValue("5MB") DataSize maxFileSize,
      @DefaultValue("40000000") long maxPixels,
      @DefaultValue("60") int uploadsPerHour,
      @DefaultValue("filesystem") String storage,
      @DefaultValue S3 s3) {
    public Media {
      if (!Set.of("filesystem", "s3").contains(storage)) {
        throw new IllegalArgumentException("dukkan.media.storage must be filesystem or s3");
      }
      if ("s3".equals(storage) && s3.bucket() == null) {
        throw new IllegalArgumentException("dukkan.media.s3.bucket (env DUKKAN_MEDIA_S3_BUCKET) is required for storage s3");
      }
      if (directory == null) {
        directory = Path.of(System.getProperty("java.io.tmpdir"), "dukkan-media");
      }
      if (publicBaseUrl != null && publicBaseUrl.isBlank()) {
        publicBaseUrl = null;
      }
      if (publicBaseUrl != null) {
        URI uri = URI.create(publicBaseUrl);
        if (!uri.isAbsolute() || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
          throw new IllegalArgumentException("dukkan.media.public-base-url must be an absolute http(s) URL");
        }
      }
      if (maxFileSize.toBytes() < 1 || maxPixels < 1 || uploadsPerHour < 1) {
        throw new IllegalArgumentException("dukkan.media limits must be >= 1");
      }
    }
  }

  /**
   * S3-compatible object storage for media ({@code dukkan.media.storage=s3}): AWS S3, Cloudflare R2,
   * MinIO, Ceph, …. {@code endpoint} is empty for AWS; set it (and usually {@code pathStyle}) for
   * other providers. Without {@code accessKey}/{@code secretKey} the AWS default credentials chain
   * is used (environment, web identity / IRSA, instance profile). {@code createBucket} is for local
   * development only.
   */
  public record S3(
      @Nullable String endpoint,
      @DefaultValue("eu-central-1") String region,
      @Nullable String bucket,
      @Nullable String accessKey,
      @Nullable String secretKey,
      @DefaultValue("false") boolean pathStyle,
      @DefaultValue("false") boolean createBucket) {
    public S3 {
      endpoint = blankToNull(endpoint);
      bucket = blankToNull(bucket);
      accessKey = blankToNull(accessKey);
      secretKey = blankToNull(secretKey);
      if ((accessKey == null) != (secretKey == null)) {
        throw new IllegalArgumentException("dukkan.media.s3.access-key and secret-key must be set together");
      }
    }

    @Override
    public String toString() {
      return "S3[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + ", accessKey=" + (accessKey == null ? "unset" : "***") + "]";
    }
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  /**
   * AI product capture (module {@code capture}): {@code draftsPerHour} per user and instance. The
   * model is configured under {@code dukkan.ai.anthropic}; without an API key the rule-based parser
   * answers alone.
   */
  public record Capture(@DefaultValue("30") int draftsPerHour) {
    public Capture {
      if (draftsPerHour < 1) {
        throw new IllegalArgumentException("dukkan.capture.drafts-per-hour must be >= 1");
      }
    }
  }

  /**
   * Claude API settings ({@code dukkan.ai.anthropic}) for product capture and translation. {@code effort} is {@code low}, {@code medium} or
   * {@code high}. {@code serverSideFallbacks} lets the API retry a declined request on the model it
   * recommends for that case ({@code fallbacks: "default"}); {@code baseUrl} is only for tests or a
   * gateway.
   */
  public record Anthropic(
      @Nullable String apiKey,
      @DefaultValue("claude-opus-5-5") String model,
      @DefaultValue("low") String effort,
      @DefaultValue("PT30S") Duration timeout,
      @DefaultValue("8000") long maxTokens,
      @DefaultValue("true") boolean serverSideFallbacks,
      @Nullable String baseUrl) {
    public Anthropic {
      if (apiKey != null && apiKey.isBlank()) {
        apiKey = null;
      }
      if (baseUrl != null && baseUrl.isBlank()) {
        baseUrl = null;
      }
      if (!Set.of("low", "medium", "high").contains(effort)) {
        throw new IllegalArgumentException("dukkan.ai.anthropic.effort must be low, medium or high");
      }
      if (timeout.isNegative() || timeout.isZero() || maxTokens < 1024) {
        throw new IllegalArgumentException("dukkan.ai.anthropic timeout must be positive and max-tokens >= 1024");
      }
    }

    public boolean enabled() {
      return apiKey != null;
    }

    @Override
    public String toString() {
      return "Anthropic[apiKey=" + (apiKey == null ? "unset" : "***") + ", model=" + model + ", effort=" + effort + "]";
    }
  }
}
