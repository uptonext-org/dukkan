import type { Problem } from './types';

/**
 * - `http`: the server answered with a non-2xx status.
 * - `network`: no response (offline, DNS, connection refused).
 * - `timeout`: the request was aborted after the configured timeout.
 */
export type ApiErrorKind = 'http' | 'network' | 'timeout';

export interface ApiErrorInit {
  kind: ApiErrorKind;
  status: number;
  code: string;
  title?: string | undefined;
  detail?: string | undefined;
  fieldErrors?: Record<string, string> | undefined;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  /** HTTP status, or 0 when no response was received. */
  readonly status: number;
  /** Machine-readable error code from the problem document (or a synthetic one). */
  readonly code: string;
  readonly title: string | undefined;
  readonly detail: string | undefined;
  /** Validation messages keyed by request field name. */
  readonly fieldErrors: Readonly<Record<string, string>>;

  constructor(init: ApiErrorInit) {
    super(init.detail ?? init.title ?? init.code, { cause: init.cause });
    this.name = 'ApiError';
    this.kind = init.kind;
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.fieldErrors = init.fieldErrors ?? {};
  }

  get isClientError(): boolean {
    return this.kind === 'http' && this.status >= 400 && this.status < 500;
  }

  get isOffline(): boolean {
    return this.kind === 'network' || this.kind === 'timeout';
  }

  static fromProblem(status: number, problem: Partial<Problem> | undefined): ApiError {
    const fieldErrors: Record<string, string> = {};
    for (const entry of problem?.errors ?? []) {
      // Keep the first message per field.
      fieldErrors[entry.field] ??= entry.message;
    }
    return new ApiError({
      kind: 'http',
      status,
      code: problem?.code ?? `HTTP_${status}`,
      title: problem?.title,
      detail: problem?.detail,
      fieldErrors,
    });
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isOfflineError(error: unknown): boolean {
  return isApiError(error) && error.isOffline;
}
