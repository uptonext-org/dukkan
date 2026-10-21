package de.dukkan.identity.application;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Account registration, login, lookup and profile preferences. */
public interface IdentityService {

  /** Creates an account and signs it in; fails with {@code EMAIL_TAKEN} if the email exists. */
  AuthResult register(RegisterCommand command);

  /** Signs in; fails with {@code INVALID_CREDENTIALS} without revealing whether the email exists. */
  AuthResult login(String email, String password);

  UserView getUser(UUID userId);

  Optional<UserView> findByEmail(String email);

  /** Updates the given (non-null) fields; the locale must be an enabled content locale. */
  UserView updateProfile(UUID userId, @Nullable String firstName, @Nullable String lastName, @Nullable String preferredLocale);
}
