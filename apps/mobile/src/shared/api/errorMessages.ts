import { isApiError } from './ApiError';

/** Every error `code` in the contract (plus client-side ones) has a translation under `errors.<code>`. */
export const KNOWN_ERROR_CODES = [
  'NETWORK_ERROR',
  'TIMEOUT',
  'VALIDATION_FAILED',
  'UNAUTHORIZED',
  'INVALID_CREDENTIALS',
  'FORBIDDEN',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'INTERNAL_ERROR',
  'EMAIL_TAKEN',
  'SLOT_FULL',
  'LISTING_EXISTS',
  'IDEMPOTENCY_KEY_REUSED',
  'CONCURRENT_MODIFICATION',
  'CONFLICT',
  'PRODUCT_UNAVAILABLE',
  'MAX_QUANTITY_EXCEEDED',
  'MARKET_NOT_ACTIVE',
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
] as const;

export type KnownErrorCode = (typeof KNOWN_ERROR_CODES)[number];

/**
 * Validation codes of VALIDATION_FAILED field errors that get their own message under
 * `errors.field.<code>` (other field codes fall back to the generic VALIDATION_FAILED text).
 * MaxUtf8Bytes: password longer than 72 UTF-8 bytes. Future: promotion end date not in the future.
 */
export const KNOWN_FIELD_CODES = ['MaxUtf8Bytes', 'Future'] as const;

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
