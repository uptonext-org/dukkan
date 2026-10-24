package de.dukkan.identity.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Account registration, login, lookup and profile preferences. */
public interface IdentityService {

  /** Creates an account and signs it in; fails with {@code EMAIL_TAKEN} if the email exists. */
  AuthResult register(RegisterCommand command);

  /** Signs in; fails with {@code INVALID_CREDENTIALS} without revealing whether the email exists. */
  AuthResult login(String email, String password);

  /**
   * Exchanges a refresh token for new tokens and revokes it (rotation). Presenting a token that was
   * already rotated revokes the whole sign-in (token theft). Fails with {@code INVALID_REFRESH_TOKEN}.
   */
  AuthResult refresh(String refreshToken);

  /** Ends the sign-in the refresh token belongs to; unknown tokens are ignored. */
  void logout(String refreshToken);

  /** Sends a reset link if the account exists; never reveals whether it does. */
  void requestPasswordReset(String email);

  /** Sets a new password with a reset token and signs out everywhere; fails with {@code INVALID_RESET_TOKEN}. */
  void resetPassword(String token, String newPassword);

  /** Changes the password after checking the current one; other sign-ins are ended. */
  void changePassword(UUID userId, String currentPassword, String newPassword);

  /** Signs in with an Apple or Google ID token, linking or creating the account by verified email. */
  AuthResult socialSignIn(SocialSignInCommand command);

  UserView getUser(UUID userId);

  Optional<UserView> findByEmail(String email);

  /** Adds a role (idempotent), e.g. ADMIN for operators. Takes effect with the next token. */
  UserView grantRole(UUID userId, String role);

  /** Whether any active account has the role. */
  boolean anyActiveWithRole(String role);

  /**
   * Confirms that the signed-in person may delete the account: the current password, or nothing
   * for accounts that only sign in with Apple/Google. Fails with field {@code password}, code
   * {@code Incorrect}.
   */
  void confirmAccountOwner(UUID userId, @Nullable String password);

  /**
   * Confirms the email address with the token from the verification mail (single use, valid for
   * {@code dukkan.security.auth.email-verification-ttl}). Fails with field {@code token}, code
   * {@code INVALID_VERIFICATION_TOKEN}.
   */
  void verifyEmail(String token);

  /** Sends a new verification mail unless the address is already confirmed. */
  void resendVerification(UUID userId);

  boolean isEmailVerified(UUID userId);

  /**
   * With {@code dukkan.security.auth.require-verified-email}, fails with 422 {@code
   * EMAIL_NOT_VERIFIED} for an unconfirmed address (ordering, opening a market); otherwise a no-op.
   */
  void requireVerifiedEmail(UUID userId);

  /** True for deleted (anonymised) and unknown accounts. */
  boolean isDeleted(UUID userId);

  /**
   * For code that writes data for a user outside their request (event handlers): locks the account
   * row until the transaction ends, so a deletion running at the same time either happens first
   * (then this returns false) or waits and removes what was written. False for deleted and unknown
   * accounts.
   */
  boolean holdActiveAccount(UUID userId);

  /** Locks the account row for its deletion (first step, before personal data is removed). */
  void lockForDeletion(UUID userId);

  /** Providers (APPLE, GOOGLE) linked to the account. */
  List<String> linkedProviders(UUID userId);

  /** Removes everything that identifies the user and ends all sessions (B-51). */
  void anonymize(UUID userId);

  /** Updates the given (non-null) fields; the locale must be an enabled content locale. */
  UserView updateProfile(UUID userId, @Nullable String firstName, @Nullable String lastName, @Nullable String preferredLocale);
}
