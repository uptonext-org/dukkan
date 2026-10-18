package de.dukkan.identity.application;

import java.util.UUID;

/** Account registration, login and lookup. */
public interface IdentityService {

  /** Creates an account and signs it in; fails with {@code EMAIL_TAKEN} if the email exists. */
  AuthResult register(RegisterCommand command);

  /** Signs in; fails with {@code INVALID_CREDENTIALS} without revealing whether the email exists. */
  AuthResult login(String email, String password);

  UserView getUser(UUID userId);
}
