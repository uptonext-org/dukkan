import { ApiError } from '../ApiError';
import { buildUrl, createFetchApiClient } from '../client';
import { errorMessageKey } from '../errorMessages';
import { shouldRetry } from '../queryClient';

const BASE = 'http://localhost:8080/api/v1';

function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

describe('createFetchApiClient', () => {
  it('builds the URL, sends JSON and injects the bearer token', async () => {
    const fetchImpl = jest.fn<Promise<Response>, Parameters<typeof fetch>>(() =>
      Promise.resolve(jsonResponse(200, { ok: true })),
    );
    const client = createFetchApiClient({ baseUrl: `${BASE}/`, getLanguage: () => 'de',
      getToken: () => 'jwt-123', fetchImpl });

    const result = await client.request<{ ok: boolean }>({
      method: 'PUT',
      path: '/cart/items/p1',
      query: { a: 1, skip: undefined, empty: '' },
      body: { quantity: 2 },
    });

    expect(result).toEqual({ ok: true });
    const [url, init] = fetchImpl.mock.calls[0] ?? [];
    expect(url).toBe(`${BASE}/cart/items/p1?a=1`);
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer jwt-123');
    expect(headers['Content-Type']).toBe('application/json');
    expect(init?.body).toBe(JSON.stringify({ quantity: 2 }));
  });

  it('sends the UI language as Accept-Language on every request', async () => {
    let language = 'ar';
    const fetchImpl = jest.fn<Promise<Response>, Parameters<typeof fetch>>(() => Promise.resolve(jsonResponse(200, [])));
    const client = createFetchApiClient({ baseUrl: BASE, getToken: () => null, getLanguage: () => language, fetchImpl });
    await client.request({ method: 'GET', path: '/market-types' });
    language = 'en';
    await client.request({ method: 'POST', path: '/auth/login', body: {} });
    const sent = fetchImpl.mock.calls.map(([, init]) => (init?.headers as Record<string, string>)['Accept-Language']);
    expect(sent).toEqual(['ar', 'en']);
  });

  it('omits the Authorization header when signed out', async () => {
    const fetchImpl = jest.fn<Promise<Response>, Parameters<typeof fetch>>(() =>
      Promise.resolve(jsonResponse(200, [])),
    );
    const client = createFetchApiClient({ baseUrl: BASE, getLanguage: () => 'de',
      getToken: () => null, fetchImpl });
    await client.request({ method: 'GET', path: '/categories' });
    const headers = fetchImpl.mock.calls[0]?.[1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });

  it('maps problem+json to a typed ApiError with field errors', async () => {
    const problem = {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      code: 'VALIDATION_FAILED',
      detail: 'Request validation failed',
      errors: [
        { field: 'postalCode', message: 'must match ^[0-9]{5}$' },
        { field: 'city', message: 'must not be blank' },
      ],
    };
    const client = createFetchApiClient({
      baseUrl: BASE,
      getLanguage: () => 'de',
      getToken: () => null,
      fetchImpl: () => Promise.resolve(jsonResponse(400, problem, 'application/problem+json')),
    });

    const error = await client
      .request({ method: 'POST', path: '/addresses', body: {} })
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.kind).toBe('http');
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.detail).toBe('Request validation failed');
    expect(apiError.fieldErrors).toEqual({ postalCode: 'must match ^[0-9]{5}$', city: 'must not be blank' });
    expect(apiError.isClientError).toBe(true);
  });

  it('falls back to HTTP_<status> when the body is not a problem document', async () => {
    const client = createFetchApiClient({
      baseUrl: BASE,
      getLanguage: () => 'de',
      getToken: () => null,
      fetchImpl: () =>
        Promise.resolve(new Response('oops', { status: 502, headers: { 'Content-Type': 'text/plain' } })),
    });
    await expect(client.request({ method: 'GET', path: '/cart' })).rejects.toMatchObject({
      kind: 'http',
      status: 502,
      code: 'HTTP_502',
    });
  });

  it('calls onUnauthorized for a 401 on an authenticated request only', async () => {
    const onUnauthorized = jest.fn();
    const unauthorized = () =>
      Promise.resolve(
        jsonResponse(401, { type: 'about:blank', title: 'Unauthorized', status: 401, code: 'UNAUTHORIZED' }),
      );

    const authed = createFetchApiClient({
      baseUrl: BASE,
      getLanguage: () => 'de',
      getToken: () => 'expired',
      onUnauthorized,
      fetchImpl: unauthorized,
    });
    await expect(authed.request({ method: 'GET', path: '/cart' })).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);

    const anonymous = createFetchApiClient({
      baseUrl: BASE,
      getLanguage: () => 'de',
      getToken: () => null,
      onUnauthorized,
      fetchImpl: unauthorized,
    });
    await expect(anonymous.request({ method: 'POST', path: '/auth/login', body: {} })).rejects.toMatchObject({
      status: 401,
    });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('resolves 204 No Content to undefined', async () => {
    const client = createFetchApiClient({
      baseUrl: BASE,
      getLanguage: () => 'de',
      getToken: () => 't',
      fetchImpl: () => Promise.resolve(new Response(null, { status: 204 })),
    });
    await expect(client.request({ method: 'DELETE', path: '/cart' })).resolves.toBeUndefined();
  });

  it('turns a network TypeError into an offline ApiError', async () => {
    const client = createFetchApiClient({
      baseUrl: BASE,
      getLanguage: () => 'de',
      getToken: () => null,
      fetchImpl: () => Promise.reject(new TypeError('Network request failed')),
    });
    const error = (await client
      .request({ method: 'GET', path: '/categories' })
      .catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe('network');
    expect(error.status).toBe(0);
    expect(error.isOffline).toBe(true);
  });

  it('aborts after the timeout and reports a timeout ApiError', async () => {
    jest.useFakeTimers();
    try {
      const fetchImpl = (_url: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new Error('Aborted')));
        });
      const client = createFetchApiClient({
        baseUrl: BASE,
        getLanguage: () => 'de',
      getToken: () => null,
        timeoutMs: 1000,
        fetchImpl,
      });
      const pending = client.request({ method: 'GET', path: '/products' }).catch((e: unknown) => e);
      jest.advanceTimersByTime(1000);
      const error = (await pending) as ApiError;
      expect(error).toBeInstanceOf(ApiError);
      expect(error.kind).toBe('timeout');
      expect(error.code).toBe('TIMEOUT');
    } finally {
      jest.useRealTimers();
    }
  });

  it('propagates caller cancellation without wrapping it', async () => {
    const controller = new AbortController();
    const abortError = new Error('Aborted by caller');
    const fetchImpl = (_url: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(abortError));
      });
    const client = createFetchApiClient({ baseUrl: BASE, getLanguage: () => 'de',
      getToken: () => null, fetchImpl });
    const pending = client.request({ method: 'GET', path: '/products', signal: controller.signal });
    controller.abort();
    await expect(pending).rejects.toBe(abortError);
  });
});

describe('errorMessageKey', () => {
  it('maps codes, known validation field codes and unknown errors to translation keys', () => {
    const problem = (code: string, errors?: { field: string; code: string; message: string }[]) =>
      ApiError.fromProblem(code === 'VALIDATION_FAILED' ? 400 : 409, { code, ...(errors ? { errors } : {}) });
    expect(errorMessageKey(problem('IDEMPOTENCY_KEY_REUSED'))).toBe('errors.IDEMPOTENCY_KEY_REUSED');
    expect(errorMessageKey(problem('VALIDATION_FAILED', [{ field: 'password', code: 'MaxUtf8Bytes', message: 'too long' }]))).toBe('errors.field.MaxUtf8Bytes');
    expect(errorMessageKey(problem('VALIDATION_FAILED', [{ field: 'validUntil', code: 'Future', message: 'must be in the future' }]))).toBe('errors.field.Future');
    expect(errorMessageKey(problem('VALIDATION_FAILED', [{ field: 'city', code: 'NotBlank', message: 'blank' }]))).toBe('errors.VALIDATION_FAILED');
    expect(errorMessageKey(problem('SOMETHING_NEW'))).toBe('errors.UNKNOWN');
    expect(errorMessageKey(new Error('x'))).toBe('errors.UNKNOWN');
  });
});

describe('buildUrl', () => {
  it('sends arrays comma-separated (style form, explode false) and skips empty ones', () => {
    expect(buildUrl(BASE, '/markets', { types: ['BAKERY', 'BUTCHER'], cuisines: [] })).toBe(`${BASE}/markets?types=BAKERY,BUTCHER`);
  });

  it('encodes query values', () => {
    expect(buildUrl(BASE, '/products', { q: 'oat & honey', promotedOnly: true })).toBe(
      `${BASE}/products?q=oat%20%26%20honey&promotedOnly=true`,
    );
  });
});

describe('shouldRetry', () => {
  it('never retries 4xx and retries transient errors twice', () => {
    expect(shouldRetry(0, new ApiError({ kind: 'http', status: 404, code: 'NOT_FOUND' }))).toBe(false);
    expect(shouldRetry(0, new ApiError({ kind: 'http', status: 503, code: 'HTTP_503' }))).toBe(true);
    expect(shouldRetry(1, new ApiError({ kind: 'network', status: 0, code: 'NETWORK_ERROR' }))).toBe(true);
    expect(shouldRetry(2, new ApiError({ kind: 'network', status: 0, code: 'NETWORK_ERROR' }))).toBe(false);
  });
});
