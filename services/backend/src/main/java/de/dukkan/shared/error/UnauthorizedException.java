package de.dukkan.shared.error;

/** Authentication is missing or not accepted (HTTP 401, {@code UNAUTHORIZED} or a more specific code). */
public final class UnauthorizedException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UnauthorizedException(String message) {
    super(ErrorCodes.UNAUTHORIZED, message);
  }

  /** A 401 with a more specific code, e.g. {@code INVALID_REFRESH_TOKEN}. */
  public UnauthorizedException(String code, String message) {
    super(code, message);
  }
}
