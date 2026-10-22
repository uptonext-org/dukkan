package de.dukkan.identity.application;

import org.jspecify.annotations.Nullable;

/** Port: account emails. Implementations must never log the link outside development. */
public interface AccountMailer {

  /** Sends the password reset link in the user's language (de/en/ar, German by default). */
  void sendPasswordReset(String email, String firstName, @Nullable String locale, String link);

  /** Sends the link that confirms the email address (B-39b), in the user's language. */
  void sendEmailVerification(String email, String firstName, @Nullable String locale, String link);
}
