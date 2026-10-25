import { isApiError } from './ApiError';

/** Every error `code` in the contract (plus client-side ones) has a translation under `errors.<code>`. */
export const KNOWN_ERROR_CODES = [
  'NETWORK_ERROR',
  'TIMEOUT',
  'VALIDATION_FAILED',
  'UNAUTHORIZED',
  'INVALID_CREDENTIALS',
  'INVALID_REFRESH_TOKEN',
  'INVALID_SOCIAL_TOKEN',
  'SOCIAL_PROVIDER_DISABLED',
  'SOCIAL_EMAIL_MISSING',
  'SOCIAL_SIGN_IN_FAILED',
  'FORBIDDEN',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'INTERNAL_ERROR',
  'EMAIL_TAKEN',
  'TERMS_NOT_ACCEPTED',
  'SLOT_FULL',
  'LISTING_EXISTS',
  'IDEMPOTENCY_KEY_REUSED',
  'RATE_LIMITED',
  'CONCURRENT_MODIFICATION',
  'CONFLICT',
  'PRODUCT_UNAVAILABLE',
  'MAX_QUANTITY_EXCEEDED',
  'MARKET_NOT_ACTIVE',
  'ORDERS_NOT_ACCEPTED',
  'FEEDBACK_DISABLED',
  'SLOT_UNAVAILABLE',
  'FULFILMENT_NOT_OFFERED',
  'ADDRESS_REQUIRED',
  'ADDRESS_OUTSIDE_DELIVERY_AREA',
  'SLOT_CUTOFF_PASSED',
  'SLOT_TYPE_MISMATCH',
  'SLOT_NOT_RESERVED',
  'CART_EMPTY',
  'MINIMUM_ORDER_NOT_MET',
  'ORDER_NOT_CANCELLABLE',
  'INVALID_STATUS_TRANSITION',
  'UNSUPPORTED_MEDIA_TYPE',
  'FILE_TOO_LARGE',
  'INVALID_IMAGE',
  'IMAGE_TOO_LARGE',
  'TRANSLATION_UNAVAILABLE',
  'SHOPPING_LIST_AI_UNAVAILABLE',
  'ACCOUNT_HAS_OPEN_ORDERS',
  'ACCOUNT_OWNS_MARKETS',
  'EMAIL_NOT_VERIFIED',
] as const;

export type KnownErrorCode = (typeof KNOWN_ERROR_CODES)[number];

/**
 * Validation codes of VALIDATION_FAILED field errors that get their own message under
 * `errors.field.<code>` (other field codes fall back to the generic VALIDATION_FAILED text).
 * MaxUtf8Bytes: password longer than 72 UTF-8 bytes. Future: promotion end date not in the future.
 * INVALID_RESET_TOKEN: reset link used or expired. Incorrect: wrong current password.
 */
export const KNOWN_FIELD_CODES = ['MaxUtf8Bytes', 'Future', 'INVALID_RESET_TOKEN', 'Incorrect', 'INVALID_VERIFICATION_TOKEN'] as const;

export type KnownFieldCode = (typeof KNOWN_FIELD_CODES)[number];

const KNOWN = new Set<string>(KNOWN_ERROR_CODES);
const KNOWN_FIELD = new Set<string>(KNOWN_FIELD_CODES);

export type ErrorMessageKey = `errors.${KnownErrorCode | 'UNKNOWN'}` | `errors.field.${KnownFieldCode}`;

/** First known validation code among the field errors, e.g. `{ field: 'password', code: 'MaxUtf8Bytes' }`. */
export function knownFieldError(error: unknown): { field: string; code: KnownFieldCode } | undefined {
  if (!isApiError(error)) {
    return undefined;
  }
  for (const [field, code] of Object.entries(error.fieldCodes)) {
    if (KNOWN_FIELD.has(code)) {
      return { field, code: code as KnownFieldCode };
    }
  }
  return undefined;
}

/**
 * i18n key for any error thrown by the API layer, e.g. `errors.SLOT_FULL`, `errors.field.Future`
 * for a VALIDATION_FAILED with a known field code, or `errors.UNKNOWN`.
 */
export function errorMessageKey(error: unknown): ErrorMessageKey {
  if (isApiError(error) && KNOWN.has(error.code)) {
    const field = error.code === 'VALIDATION_FAILED' ? knownFieldError(error) : undefined;
    return field !== undefined ? `errors.field.${field.code}` : `errors.${error.code as KnownErrorCode}`;
  }
  return 'errors.UNKNOWN';
}
