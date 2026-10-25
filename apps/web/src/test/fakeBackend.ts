import type { Session, SessionStore } from '../api/client';

export interface Call {
  method: string;
  path: string;
  headers: Record<string, string>;
  body: unknown;
}

type Handler = (call: Call) => unknown;

/** A fetch replacement keyed by "METHOD /path?query" (without /api/v1); unknown routes answer 404. */
export function fakeBackend(routes: Record<string, Handler | object>) {
  const calls: Call[] = [];
  const fetch: typeof globalThis.fetch = (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const path = url.replace(/^\/api\/v1/, '');
    const method = init?.method ?? 'GET';
    const headers = (init?.headers ?? {}) as Record<string, string>;
    let body: unknown = init?.body;
    if (typeof body === 'string') {
      body = JSON.parse(body);
    }
    const call = { method, path, headers, body };
    calls.push(call);
    const route = routes[`${method} ${path}`] ?? routes[`${method} ${path.split('?')[0] ?? ''}`];
    if (route === undefined) {
      return Promise.resolve(json(404, { status: 404, code: 'NOT_FOUND', detail: `No fake for ${method} ${path}` }));
    }
    const result: unknown = typeof route === 'function' ? (route as Handler)(call) : route;
    if (isResponse(result)) {
      return Promise.resolve(json(result.status, result.body));
    }
    return Promise.resolve(json(200, result));
  };
  return { fetch, calls };
}

const RESPONSE = Symbol('response');

function isResponse(value: unknown): value is { status: number; body?: unknown } {
  return typeof value === 'object' && value !== null && RESPONSE in value;
}

/** An explicit status, e.g. `respond(401, problem)`. */
export function respond(status: number, body?: unknown) {
  return { [RESPONSE]: true, status, body };
}

function json(status: number, body: unknown): Response {
  return new Response(status === 204 || body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function memoryStore(initial: Session | null = null): SessionStore & { current: Session | null } {
  const store = {
    current: initial,
    load: () => store.current,
    save: (session: Session | null) => {
      store.current = session;
    },
  };
  return store;
}

export const merchantUser = { id: 'u1', email: 'mert@example.de', firstName: 'Mert', lastName: 'Kaya', roles: ['MERCHANT' as const] };
export const adminUser = { id: 'u2', email: 'admin@example.de', firstName: 'Ada', lastName: 'Admin', roles: ['ADMIN' as const] };

export function market(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'm1',
    slug: 'anatolia',
    name: 'Anatolia Frischmarkt',
    status: 'ACTIVE',
    primaryLocale: 'de',
    addressLine: 'Steindamm 30, 20099 Hamburg',
    type: { code: 'GROCERY', name: 'Lebensmittel' },
    imprint: { legalName: 'Anatolia GmbH', addressLine: 'Steindamm 30', registerInfo: 'HRB 1' },
    phone: '+49 40 1234567',
    review: {
      complete: true,
      items: [
        { code: 'LEGAL_NAME', ok: true, required: true },
        { code: 'POSTAL_ADDRESS', ok: true, required: true },
        { code: 'PHONE', ok: true, required: true },
        { code: 'REGISTER_INFO', ok: true, required: false },
      ],
    },
    ...overrides,
  };
}
