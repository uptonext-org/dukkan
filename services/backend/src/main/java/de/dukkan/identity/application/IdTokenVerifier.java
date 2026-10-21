package de.dukkan.identity.application;

import org.jspecify.annotations.Nullable;

/** Port: verifies an OpenID Connect ID token from Apple or Google (signature, issuer, audience, expiry). */
public interface IdTokenVerifier {

  /** Supported identity providers. */
  enum Provider {
    APPLE,
    GOOGLE
  }

  /** The claims Dukkan uses from a valid token. */
  record VerifiedIdToken(
      String subject, @Nullable String email, boolean emailVerified, @Nullable String givenName, @Nullable String familyName) {}

  boolean enabled(Provider provider);

  /**
   * Returns the verified claims or throws {@code UnauthorizedException} ({@code INVALID_SOCIAL_TOKEN}).
   * When {@code nonce} is given, the token's {@code nonce} claim must equal it or its SHA-256 hex
   * (Apple's native flow hashes the nonce, Google's does not).
   */
  VerifiedIdToken verify(Provider provider, String idToken, @Nullable String nonce);
}
