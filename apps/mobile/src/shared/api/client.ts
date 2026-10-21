import { ApiError } from './ApiError';
import type { Problem } from './types';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** Arrays are sent comma-separated (`style: form, explode: false` in the contract). */
export type QueryValue = string | number | boolean | readonly string[] | null | undefined;

export interface ApiRequest {
  method: HttpMethod;
  /** Path relative to the base URL, starting with `/`, e.g. `/products`. */
  path: string;
  query?: Readonly<Record<string, QueryValue>> | undefined;
  /**
   * JSON-serialised, except a `FormData` (multipart upload), which is passed through unchanged and
   * without a `Content-Type` header so that the platform adds `multipart/form-data; boundary=…`.
   */
  body?: unknown;
  headers?: Readonly<Record<string, string>> | undefined;
  /** Caller-provided cancellation (e.g. from React Query). */
  signal?: AbortSignal | undefined;
  /** Overrides the client's timeout, e.g. for uploads. */
  timeoutMs?: number | undefined;
  /** Send no bearer token and never refresh on 401 (sign-in, token refresh, logout). */
  anonymous?: boolean | undefined;
}

/** Transport abstraction. Screens never call it directly; they use the typed `DukkanApi`. */
export interface ApiClient {
  request<T>(request: ApiRequest): Promise<T>;
}

export interface FetchApiClientOptions {
  baseUrl: string;
  /** Returns the current bearer token, or null when signed out. */
  getToken: () => string | null;
  /** Current UI language (`de`, `en`, `ar`), sent as `Accept-Language` on every request. */
  getLanguage: () => string;
  /**
   * Called on a 401 for an authenticated request: `refreshed` retries the request once with the new
   * token, `expired` ends the session (`onUnauthorized`), `unavailable` (offline) keeps it.
   */
  refreshSession?: (() => Promise<'refreshed' | 'expired' | 'unavailable'>) | undefined;
  /** Called when an authenticated request is rejected with 401 and the session could not be renewed. */
  onUnauthorized?: (() => void) | undefined;
  timeoutMs?: number | undefined;
  fetchImpl?: typeof fetch | undefined;
}

export const DEFAULT_TIMEOUT_MS = 15_000;

export function buildUrl(baseUrl: string, path: string, query?: ApiRequest['query']): string {
  const base = baseUrl.replace(/\/+$/, '');
  const params: string[] = [];
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') {
        continue;
      }
      if (Array.isArray(value)) {
        const list = value as readonly string[];
        if (list.length > 0) {
          params.push(`${encodeURIComponent(key)}=${list.map(encodeURIComponent).join(',')}`);
        }
        continue;
      }
      params.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    }
  }
  return `${base}${path}${params.length > 0 ? `?${params.join('&')}` : ''}`;
}

function isJsonContentType(contentType: string | null): boolean {
  return contentType !== null && /application\/(problem\+)?json/i.test(contentType);
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text.length === 0) {
    return undefined;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return undefined;
  }
}

/** True for a multipart body. `FormData` is a global in React Native and in Node ≥ 18. */
export function isFormData(body: unknown): body is FormData {
  return typeof FormData !== 'undefined' && body instanceof FormData;
}

function encodeBody(body: unknown): FormData | string | null {
  if (body === undefined) {
    return null;
  }
  return isFormData(body) ? body : JSON.stringify(body);
}

function isProblemLike(value: unknown): value is Partial<Problem> {
  return typeof value === 'object' && value !== null;
}

export function createFetchApiClient(options: FetchApiClientOptions): ApiClient {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const fetchImpl = options.fetchImpl ?? fetch;

  const send = async <T>(request: ApiRequest, retried: boolean): Promise<T> => {
    const token = request.anonymous === true ? null : options.getToken();
    const headers: Record<string, string> = {
      Accept: 'application/json, application/problem+json',
      'Accept-Language': options.getLanguage(),
      ...request.headers,
    };
    if (request.body !== undefined && !isFormData(request.body)) {
      headers['Content-Type'] = 'application/json';
    }
    if (token !== null) {
      headers.Authorization = `Bearer ${token}`;
    }

    const controller = new AbortController();
    // Object (not a `let`) so the flag set inside the timer is visible to control-flow analysis.
    const timeout = { fired: false };
    const timer = setTimeout(() => {
      timeout.fired = true;
      controller.abort();
    }, request.timeoutMs ?? timeoutMs);
    const onCallerAbort = () => controller.abort();
    request.signal?.addEventListener('abort', onCallerAbort);

    let response: Response;
    try {
      response = await fetchImpl(buildUrl(options.baseUrl, request.path, request.query), {
        method: request.method,
        headers,
        body: encodeBody(request.body),
        signal: controller.signal,
      });
    } catch (cause) {
      if (timeout.fired) {
        throw new ApiError({
          kind: 'timeout',
          status: 0,
          code: 'TIMEOUT',
          detail: 'The server took too long to respond.',
          cause,
        });
      }
      if (request.signal?.aborted === true) {
        // Cancellation requested by the caller: propagate as-is.
        throw cause;
      }
      throw new ApiError({
        kind: 'network',
        status: 0,
        code: 'NETWORK_ERROR',
        detail: 'No connection to the server.',
        cause,
      });
    } finally {
      clearTimeout(timer);
      request.signal?.removeEventListener('abort', onCallerAbort);
    }

    const contentType = response.headers.get('content-type');
    const payload = isJsonContentType(contentType) ? await readJson(response) : undefined;

    if (!response.ok) {
      if (response.status === 401 && token !== null) {
        const outcome =
          options.refreshSession !== undefined && !retried ? await options.refreshSession() : 'expired';
        if (outcome === 'refreshed') {
          return send<T>(request, true);
        }
        if (outcome === 'expired') {
          options.onUnauthorized?.();
        }
      }
      throw ApiError.fromProblem(response.status, isProblemLike(payload) ? payload : undefined);
    }

    return payload as T;
  };

  return {
    request: <T>(request: ApiRequest) => send<T>(request, false),
  };
}
