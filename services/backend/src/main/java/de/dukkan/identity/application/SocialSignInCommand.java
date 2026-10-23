package de.dukkan.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * Sign in with Apple or Google. Names are used only when an account is created (Apple sends them
 * to the app on the first sign-in only); {@code merchant} adds the MERCHANT role then, and
 * {@code acceptedTermsVersion} must match the platform's terms version (as for registration).
 */
public record SocialSignInCommand(
    IdTokenVerifier.Provider provider,
    String idToken,
    @Nullable String nonce,
    @Nullable String firstName,
    @Nullable String lastName,
    boolean merchant,
    @Nullable String preferredLocale,
    @Nullable String acceptedTermsVersion) {

  @Override
  public String toString() {
    return "SocialSignInCommand[provider=" + provider + ", idToken=***]";
  }
}
