import { isApiError } from './ApiError';

const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: 'You appear to be offline. Check your connection and try again.',
  TIMEOUT: 'The server took too long to respond. Please try again.',
  INVALID_CREDENTIALS: 'Email or password is incorrect.',
  UNAUTHORIZED: 'Your session has expired. Please sign in again.',
  EMAIL_TAKEN: 'An account with this email already exists.',
  NOT_FOUND: 'We could not find what you were looking for.',
  SLOT_FULL: 'This delivery slot has just filled up. Please choose another one.',
  PRODUCT_UNAVAILABLE: 'This product is currently unavailable.',
  MAX_QUANTITY_EXCEEDED: 'You have reached the maximum quantity for this product.',
  ADDRESS_NOT_SERVICEABLE: "We don't deliver to this address yet.",
  SLOT_CUTOFF_PASSED: 'The order deadline for this slot has passed. Please choose another slot.',
  SLOT_NOT_RESERVED: 'Please choose a delivery slot before checking out.',
  CART_EMPTY: 'Your cart is empty.',
  MINIMUM_ORDER_NOT_MET: 'Your cart has not reached the minimum order value yet.',
  ORDER_NOT_CANCELLABLE: 'This order can no longer be cancelled.',
  VALIDATION_FAILED: 'Please check the highlighted fields.',
};

/** User-facing message for any error thrown by the API layer. */
export function errorMessage(error: unknown): string {
  if (isApiError(error)) {
    return MESSAGES[error.code] ?? error.detail ?? 'Something went wrong. Please try again.';
  }
  return 'Something went wrong. Please try again.';
}
