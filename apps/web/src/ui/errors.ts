import type { TFunction } from 'i18next';

import { ApiError } from '../api/client';

const KNOWN = new Set(['NETWORK_ERROR', 'INVALID_CREDENTIALS', 'RATE_LIMITED', 'FORBIDDEN', 'NOT_FOUND', 'VALIDATION_FAILED', 'INVALID_STATUS_TRANSITION', 'PRODUCT_DUPLICATE', 'TRANSLATION_UNAVAILABLE', 'UNSUPPORTED_MEDIA_TYPE', 'FILE_TOO_LARGE', 'INVALID_IMAGE', 'IMAGE_TOO_LARGE']);
const FILE_CODES = new Set(['Size', 'Encoding', 'Header', 'Empty', 'TooManyRows']);

/** Translated message for any error of the API client. */
export function errorMessage(t: TFunction, error: unknown): string {
  if (!(error instanceof ApiError)) {
    return t('errors.UNKNOWN');
  }
  const fileError = error.problem?.errors?.find((e) => e.field === 'file');
  if (fileError !== undefined && FILE_CODES.has(fileError.code)) {
    return t(`errors.file.${fileError.code}`);
  }
  return t(KNOWN.has(error.code) ? `errors.${error.code}` : 'errors.UNKNOWN');
}
