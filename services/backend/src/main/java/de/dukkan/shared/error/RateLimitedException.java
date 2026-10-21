package de.dukkan.shared.error;

/** Too many requests in the current window (HTTP 429, {@code RATE_LIMITED}, with {@code Retry-After}). */
public final class RateLimitedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final long retryAfterSeconds;

  public RateLimitedException(long retryAfterSeconds, String message) {
    super(ErrorCodes.RATE_LIMITED, message);
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
