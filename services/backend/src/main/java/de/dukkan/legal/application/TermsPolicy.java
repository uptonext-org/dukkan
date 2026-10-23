package de.dukkan.legal.application;

import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Whether a new account must accept the current terms, and checks that it did. */
@Service
public class TermsPolicy {

  private final PlatformSettings settings;

  public TermsPolicy(PlatformSettings settings) {
    this.settings = settings;
  }

  /** The version a new account accepts, or empty when the operator requires none. */
  public Optional<String> currentVersion() {
    return Optional.ofNullable(settings.termsVersion());
  }

  /** Throws 422 TERMS_NOT_ACCEPTED unless {@code accepted} is the current version (when one is required). */
  public Optional<String> requireAccepted(@Nullable String accepted) {
    Optional<String> current = currentVersion();
    if (current.isPresent() && !current.get().equals(accepted)) {
      throw new BusinessRuleException(
          ErrorCodes.TERMS_NOT_ACCEPTED, "Please accept the terms and the privacy notice (version " + current.get() + ").");
    }
    return current;
  }
}
