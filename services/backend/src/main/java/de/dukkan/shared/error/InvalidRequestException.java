package de.dukkan.shared.error;

/**
 * A request that is syntactically valid but references unknown codes or violates a cross-field
 * rule (HTTP 400, {@code VALIDATION_FAILED}). Rendered with one {@code errors[]} entry.
 */
public final class InvalidRequestException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final String field;
  private final String violation;

  /**
   * @param field JSON path of the offending field, e.g. {@code typeCode}
   * @param violation validation code, e.g. {@code UnknownCode}
   * @param message English message for the {@code errors[]} entry
   */
  public InvalidRequestException(String field, String violation, String message) {
    super(ErrorCodes.VALIDATION_FAILED, message);
    this.field = field;
    this.violation = violation;
  }

  public String field() {
    return field;
  }

  public String violation() {
    return violation;
  }
}
