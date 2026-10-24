package de.dukkan.shared.error;

/** Error codes defined by the shared OpenAPI contract. */
public final class ErrorCodes {

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String UNAUTHORIZED = "UNAUTHORIZED";
  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
  public static final String INVALID_SOCIAL_TOKEN = "INVALID_SOCIAL_TOKEN";
  // 400
  public static final String INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
  public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  // uploads: 413, 415, 400
  public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
  public static final String INVALID_IMAGE = "INVALID_IMAGE";
  public static final String IMAGE_TOO_LARGE = "IMAGE_TOO_LARGE";

  // 409
  public static final String CONFLICT = "CONFLICT";
  public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
  public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
  public static final String TERMS_NOT_ACCEPTED = "TERMS_NOT_ACCEPTED";
  public static final String SLOT_FULL = "SLOT_FULL";
  public static final String LISTING_EXISTS = "LISTING_EXISTS";
  public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
  // 429
  public static final String RATE_LIMITED = "RATE_LIMITED";

  // 422
  public static final String PRODUCT_UNAVAILABLE = "PRODUCT_UNAVAILABLE";
  public static final String PRODUCT_DUPLICATE = "PRODUCT_DUPLICATE";
  public static final String ACCOUNT_OWNS_MARKETS = "ACCOUNT_OWNS_MARKETS";
  public static final String INVALID_VERIFICATION_TOKEN = "INVALID_VERIFICATION_TOKEN";
  public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
  public static final String ACCOUNT_HAS_OPEN_ORDERS = "ACCOUNT_HAS_OPEN_ORDERS";
  public static final String MAX_QUANTITY_EXCEEDED = "MAX_QUANTITY_EXCEEDED";
  public static final String MARKET_NOT_ACTIVE = "MARKET_NOT_ACTIVE";
  public static final String ORDERS_NOT_ACCEPTED = "ORDERS_NOT_ACCEPTED";
  public static final String SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";
  public static final String FULFILMENT_NOT_OFFERED = "FULFILMENT_NOT_OFFERED";
  public static final String ADDRESS_REQUIRED = "ADDRESS_REQUIRED";
  public static final String ADDRESS_OUTSIDE_DELIVERY_AREA = "ADDRESS_OUTSIDE_DELIVERY_AREA";
  public static final String SLOT_CUTOFF_PASSED = "SLOT_CUTOFF_PASSED";
  public static final String SLOT_TYPE_MISMATCH = "SLOT_TYPE_MISMATCH";
  public static final String SLOT_NOT_RESERVED = "SLOT_NOT_RESERVED";
  public static final String CART_EMPTY = "CART_EMPTY";
  public static final String MINIMUM_ORDER_NOT_MET = "MINIMUM_ORDER_NOT_MET";
  public static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
  public static final String ORDER_NOT_ADJUSTABLE = "ORDER_NOT_ADJUSTABLE";
  public static final String ORDER_ADJUSTMENT_EMPTY = "ORDER_ADJUSTMENT_EMPTY";
  public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
  public static final String SOCIAL_PROVIDER_DISABLED = "SOCIAL_PROVIDER_DISABLED";
  public static final String SOCIAL_EMAIL_MISSING = "SOCIAL_EMAIL_MISSING";
  public static final String TRANSLATION_UNAVAILABLE = "TRANSLATION_UNAVAILABLE";
  public static final String SHOPPING_LIST_AI_UNAVAILABLE = "SHOPPING_LIST_AI_UNAVAILABLE";

  private ErrorCodes() {}
}
