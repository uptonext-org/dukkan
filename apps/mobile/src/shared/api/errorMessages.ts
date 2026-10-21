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

const KNOWN = new Set<string>(KNOWN_ERROR_CODES);

/** i18n key for any error thrown by the API layer, e.g. `errors.SLOT_FULL` or `errors.UNKNOWN`. */
export function errorMessageKey(error: unknown): `errors.${KnownErrorCode | 'UNKNOWN'}` {
  if (isApiError(error) && KNOWN.has(error.code)) {
    return `errors.${error.code as KnownErrorCode}`;
  }
  return 'errors.UNKNOWN';
}
