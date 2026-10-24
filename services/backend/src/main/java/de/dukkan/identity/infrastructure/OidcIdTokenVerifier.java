package de.dukkan.identity.infrastructure;

import de.dukkan.identity.application.IdTokenVerifier;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies Apple and Google ID tokens against the provider's published keys (JWKS, cached by
 * Nimbus): RS256 signature, issuer, audience (one of our client IDs), expiry (60 s skew). Defaults:
 * Google {@code https://accounts.google.com} / {@code accounts.google.com} with
 * {@code https://www.googleapis.com/oauth2/v3/certs}; Apple {@code https://appleid.apple.com} with
 * {@code https://appleid.apple.com/auth/keys}.
 */
@Component
final class OidcIdTokenVerifier implements IdTokenVerifier {

  private static final Logger LOG = LoggerFactory.getLogger(OidcIdTokenVerifier.class);

  private record Settings(List<String> clientIds, List<String> issuers, String jwkSetUri) {}

  private final Map<Provider, Settings> settings = new EnumMap<>(Provider.class);
  private final Map<Provider, NimbusJwtDecoder> decoders = new EnumMap<>(Provider.class);

  OidcIdTokenVerifier(DukkanProperties properties, Clock clock) {
    DukkanProperties.Auth auth = properties.security().auth();
    settings.put(
        Provider.GOOGLE,
        settings(auth.google(), List.of("https://accounts.google.com", "accounts.google.com"), "https://www.googleapis.com/oauth2/v3/certs"));
    settings.put(Provider.APPLE, settings(auth.apple(), List.of("https://appleid.apple.com"), "https://appleid.apple.com/auth/keys"));
    settings.forEach(
        (provider, s) -> {
          if (!s.clientIds().isEmpty()) {
            decoders.put(provider, decoder(s, clock));
            LOG.info("Sign in with {} enabled for {} client id(s)", provider, s.clientIds().size());
          }
        });
  }

  private static Settings settings(DukkanProperties.Provider provider, List<String> defaultIssuers, String defaultJwks) {
    return new Settings(
        provider.clientIds(),
        provider.issuers().isEmpty() ? defaultIssuers : provider.issuers(),
        provider.jwkSetUri() == null ? defaultJwks : provider.jwkSetUri());
  }

  private static NimbusJwtDecoder decoder(Settings s, Clock clock) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(s.jwkSetUri()).build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamps,
            jwt -> s.issuers().contains(jwt.getClaimAsString("iss")) ? OAuth2TokenValidatorResult.success() : failure("issuer"),
            jwt ->
                jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(s.clientIds()::contains)
                    ? OAuth2TokenValidatorResult.success()
                    : failure("audience")));
    return decoder;
  }

  private static OAuth2TokenValidatorResult failure(String what) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "unexpected " + what, null));
  }

  @Override
  public boolean enabled(Provider provider) {
    return decoders.containsKey(provider);
  }

  @Override
  public VerifiedIdToken verify(Provider provider, String idToken, @Nullable String nonce) {
    NimbusJwtDecoder decoder = decoders.get(provider);
    if (decoder == null) {
      throw new IllegalStateException(provider + " sign-in is not configured");
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(idToken);
    } catch (JwtException e) {
      LOG.info("Rejected {} ID token: {}", provider, e.getMessage());
      throw invalid();
    }
    // A token issued for a nonce is only accepted together with that nonce, so a token caught on
    // its way to one app session cannot be replayed without it.
    String claim = jwt.getClaimAsString("nonce");
    if (nonce != null || claim != null) {
      if (claim == null || nonce == null || !(claim.equals(nonce) || claim.equals(sha256Hex(nonce)))) {
        throw invalid();
      }
    }
    return new VerifiedIdToken(
        Objects.requireNonNull(jwt.getSubject(), "sub"),
        jwt.getClaimAsString("email"),
        isTrue(jwt.getClaims().get("email_verified")),
        jwt.getClaimAsString("given_name"),
        jwt.getClaimAsString("family_name"));
  }

  /** Google sends a boolean, Apple a string ("true"). */
  private static boolean isTrue(@Nullable Object value) {
    return Boolean.TRUE.equals(value) || "true".equals(value);
  }

  private static UnauthorizedException invalid() {
    return new UnauthorizedException(ErrorCodes.INVALID_SOCIAL_TOKEN, "The sign-in token is invalid or expired.");
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
