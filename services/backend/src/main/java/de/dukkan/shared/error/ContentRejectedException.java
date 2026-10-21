package de.dukkan.shared.error;

/**
 * Uploaded content was rejected: too large (HTTP 413, {@code FILE_TOO_LARGE}), not an accepted
 * type by its content (HTTP 415, {@code UNSUPPORTED_MEDIA_TYPE}) or not decodable / over the
 * dimension limit (HTTP 400, {@code INVALID_IMAGE} / {@code IMAGE_TOO_LARGE}).
 */
public final class ContentRejectedException extends DomainException {

  private static final long serialVersionUID = 1L;

  /** Why the content was rejected; decides the HTTP status. */
  public enum Reason {
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    INVALID
  }

  private final Reason reason;

  private ContentRejectedException(Reason reason, String code, String message) {
    super(code, message);
    this.reason = reason;
  }

  public static ContentRejectedException tooLarge(String message) {
    return new ContentRejectedException(Reason.TOO_LARGE, ErrorCodes.FILE_TOO_LARGE, message);
  }

  public static ContentRejectedException unsupportedType(String message) {
    return new ContentRejectedException(Reason.UNSUPPORTED_TYPE, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, message);
  }

  public static ContentRejectedException invalidImage(String message) {
    return new ContentRejectedException(Reason.INVALID, ErrorCodes.INVALID_IMAGE, message);
  }

  public static ContentRejectedException imageTooLarge(String message) {
    return new ContentRejectedException(Reason.INVALID, ErrorCodes.IMAGE_TOO_LARGE, message);
  }

  public Reason reason() {
    return reason;
  }
}
