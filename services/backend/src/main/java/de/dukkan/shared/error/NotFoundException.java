package de.dukkan.shared.error;

/** The resource does not exist or is not owned by the caller (HTTP 404, {@code NOT_FOUND}). */
public final class NotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(ErrorCodes.NOT_FOUND, message);
  }
}
