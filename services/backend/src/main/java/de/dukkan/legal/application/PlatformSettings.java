package de.dukkan.legal.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The operator's legal documents and how customers pay, under {@code dukkan.platform.*}. The texts
 * themselves are the operator's (published on the web site); the apps link to them.
 *
 * @param termsVersion version of the terms (AGB) and privacy notice a new account must accept; empty = none required
 * @param payment {@code SIMULATED} (no money changes hands, the apps say so) or {@code ON_SITE} (the customer
 *     pays the market at pickup or delivery; the order button reads "zahlungspflichtig bestellen")
 */
@ConfigurationProperties("dukkan.platform")
public record PlatformSettings(
    @Nullable String imprintUrl,
    @Nullable String privacyUrl,
    @Nullable String termsUrl,
    @Nullable String termsVersion,
    @Nullable String supportEmail,
    @DefaultValue("SIMULATED") PaymentMode payment) {

  public enum PaymentMode {
    SIMULATED,
    ON_SITE
  }

  public PlatformSettings {
    imprintUrl = blankToNull(imprintUrl);
    privacyUrl = blankToNull(privacyUrl);
    termsUrl = blankToNull(termsUrl);
    termsVersion = blankToNull(termsVersion);
    supportEmail = blankToNull(supportEmail);
    if (termsVersion != null && termsVersion.length() > 40) {
      throw new IllegalArgumentException("dukkan.platform.terms-version must be at most 40 characters");
    }
    if (termsVersion != null && (termsUrl == null || privacyUrl == null)) {
      throw new IllegalArgumentException("dukkan.platform.terms-version needs terms-url and privacy-url to link to");
    }
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
