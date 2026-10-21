import { ApiError, createFetchApiClient, type AuthResponse } from '../../../shared/api';
import { createMemoryTokenStorage, parseStoredCredentials } from '../../../shared/storage';
import { createAuthSession } from '../session';

const user = { id: 'u1', email: 'a@example.org', firstName: 'A', lastName: 'B', roles: ['CUSTOMER' as const] };
const tokens = (n: number): AuthResponse => ({ accessToken: `access-${n}`, tokenType: 'Bearer', expiresIn: 900, refreshToken: `refresh-${n}`, refreshExpiresIn: 2_592_000, user });

describe('auth session (B-39)', () => {
  it('stores both tokens, rotates them with one refresh at a time and keeps the session offline', async () => {
    const storage = createMemoryTokenStorage();
    const session = createAuthSession(storage);
    await session.start(tokens(1));
    let calls = 0;
    session.setRefresher((refreshToken) => {
      calls += 1;
      expect(refreshToken).toBe('refresh-1');
      return Promise.resolve(tokens(2));
    });

    const [a, b] = await Promise.all([session.refresh(), session.refresh()]);
    expect([a, b]).toEqual(['refreshed', 'refreshed']);
    expect(calls).toBe(1);
    expect(session.getToken()).toBe('access-2');
    expect(await storage.get()).toEqual({ accessToken: 'access-2', refreshToken: 'refresh-2' });

    session.setRefresher(() => Promise.reject(new ApiError({ kind: 'network', status: 0, code: 'NETWORK_ERROR', detail: 'offline' })));
    expect(await session.refresh()).toBe('unavailable');
    expect(session.getToken()).toBe('access-2');

    session.setRefresher(() => Promise.reject(new ApiError({ kind: 'http', status: 401, code: 'INVALID_REFRESH_TOKEN', detail: 'gone' })));
    expect(await session.refresh()).toBe('expired');
  });

  it('reads sessions saved by older app versions (a bare JWT, no refresh token)', async () => {
    expect(parseStoredCredentials('eyJ.jwt')).toEqual({ accessToken: 'eyJ.jwt', refreshToken: null });
    expect(parseStoredCredentials('{"accessToken":"a","refreshToken":"r"}')).toEqual({ accessToken: 'a', refreshToken: 'r' });
    expect(parseStoredCredentials('{broken')).toBeNull();
    const session = createAuthSession(createMemoryTokenStorage('old-jwt'));
    expect(await session.restore()).toBe('old-jwt');
    expect(await session.refresh()).toBe('expired');
  });
});

describe('API client with session refresh', () => {
  const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

  it('renews the access token on 401 and repeats the request once', async () => {
    const session = createAuthSession(createMemoryTokenStorage());
    await session.start(tokens(1));
    const seen: (string | undefined)[] = [];
    const fetchImpl = jest.fn((url: RequestInfo | URL, init?: RequestInit) => {
      const headers = init?.headers as Record<string, string>;
      const path = typeof url === 'string' ? url : url instanceof URL ? url.href : url.url;
      seen.push(`${path.slice(path.indexOf('/api/v1'))} ${headers.Authorization ?? '-'}`);
      if (path.endsWith('/auth/refresh')) {
        return Promise.resolve(json(200, tokens(2)));
      }
      return Promise.resolve(headers.Authorization === 'Bearer access-2' ? json(200, { ok: true }) : json(401, { status: 401, code: 'UNAUTHORIZED' }));
    });
    const onUnauthorized = jest.fn();
    const client = createFetchApiClient({
      baseUrl: 'http://localhost/api/v1',
      getToken: () => session.getToken(),
      getLanguage: () => 'de',
      refreshSession: () => session.refresh(),
      onUnauthorized,
      fetchImpl,
    });
    session.setRefresher((refreshToken) => client.request<AuthResponse>({ method: 'POST', path: '/auth/refresh', body: { refreshToken }, anonymous: true }));

    await expect(client.request({ method: 'GET', path: '/carts' })).resolves.toEqual({ ok: true });
    expect(seen).toEqual(['/api/v1/carts Bearer access-1', '/api/v1/auth/refresh -', '/api/v1/carts Bearer access-2']);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('ends the session when the refresh token is no longer valid, and never refreshes anonymous calls', async () => {
    const session = createAuthSession(createMemoryTokenStorage());
    await session.start(tokens(1));
    const fetchImpl = jest.fn(() => Promise.resolve(json(401, { status: 401, code: 'INVALID_REFRESH_TOKEN' })));
    const onUnauthorized = jest.fn(() => session.expire());
    const client = createFetchApiClient({
      baseUrl: 'http://localhost/api/v1',
      getToken: () => session.getToken(),
      getLanguage: () => 'de',
      refreshSession: () => session.refresh(),
      onUnauthorized,
      fetchImpl,
    });
    session.setRefresher((refreshToken) => client.request<AuthResponse>({ method: 'POST', path: '/auth/refresh', body: { refreshToken }, anonymous: true }));

    await expect(client.request({ method: 'POST', path: '/auth/login', body: {}, anonymous: true })).rejects.toMatchObject({ status: 401 });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await expect(client.request({ method: 'GET', path: '/me' })).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    await new Promise<void>((resolve) => setImmediate(() => resolve()));
    expect(session.getToken()).toBeNull();
  });
});
