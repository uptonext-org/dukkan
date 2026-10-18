package de.dukkan.shared.error;

/** A business rule was violated (HTTP 422), e.g. {@code MINIMUM_ORDER_NOT_MET}. */
public final class BusinessRuleException extends DomainException {

  private static final long serialVersionUID = 1L;

  public BusinessRuleException(String code, String message) {
    super(code, message);
  }
}
