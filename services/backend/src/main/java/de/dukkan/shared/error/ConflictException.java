package de.dukkan.shared.error;

/** A state conflict such as {@code EMAIL_TAKEN} or {@code SLOT_FULL} (HTTP 409). */
public final class ConflictException extends DomainException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String code, String message) {
    super(code, message);
  }
}
