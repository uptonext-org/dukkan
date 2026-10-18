package de.dukkan.shared.error;

/**
 * Login failed (HTTP 401, {@code INVALID_CREDENTIALS}). The message never reveals whether the
 * email address exists.
 */
public final class InvalidCredentialsException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super(ErrorCodes.INVALID_CREDENTIALS, "Email or password is incorrect.");
  }
}
