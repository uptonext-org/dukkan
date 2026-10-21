package de.dukkan.shared.error;

/** Authentication is required for this variant of a public endpoint (HTTP 401, {@code UNAUTHORIZED}). */
public final class UnauthorizedException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UnauthorizedException(String message) {
    super(ErrorCodes.UNAUTHORIZED, message);
  }
}
