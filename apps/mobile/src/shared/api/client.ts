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
  body?: unknown;
  headers?: Readonly<Record<string, string>> | undefined;
  /** Caller-provided cancellation (e.g. from React Query). */
  signal?: AbortSignal | undefined;
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
  /** Called when an authenticated request is rejected with 401. */
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

function isProblemLike(value: unknown): value is Partial<Problem> {
  return typeof value === 'object' && value !== null;
}

export function createFetchApiClient(options: FetchApiClientOptions): ApiClient {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const fetchImpl = options.fetchImpl ?? fetch;

  return {
    async request<T>(request: ApiRequest): Promise<T> {
      const token = options.getToken();
      const headers: Record<string, string> = {
        Accept: 'application/json, application/problem+json',
        'Accept-Language': options.getLanguage(),
        ...request.headers,
      };
      if (request.body !== undefined) {
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
      }, timeoutMs);
      const onCallerAbort = () => controller.abort();
      request.signal?.addEventListener('abort', onCallerAbort);

      let response: Response;
      try {
        response = await fetchImpl(buildUrl(options.baseUrl, request.path, request.query), {
          method: request.method,
          headers,
          body: request.body === undefined ? null : JSON.stringify(request.body),
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
          options.onUnauthorized?.();
        }
        throw ApiError.fromProblem(response.status, isProblemLike(payload) ? payload : undefined);
      }

      return payload as T;
    },
  };
}
