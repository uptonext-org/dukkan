package de.dukkan.shared.error;

/** Base type of all expected, client-visible business errors. */
public abstract sealed class DomainException extends RuntimeException
    permits BusinessRuleException,
        ConflictException,
        InvalidCredentialsException,
        InvalidRequestException,
        NotFoundException,
        UnauthorizedException {

  private static final long serialVersionUID = 1L;

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** Machine-readable error code returned as {@code code} in the problem response. */
  public String code() {
    return code;
  }
}
