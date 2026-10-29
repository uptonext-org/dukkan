import { isApiError, type AuthResponse } from '../../shared/api';
import type { StoredCredentials, TokenStorage } from '../../shared/storage';

export type SessionListener = (token: string | null) => void;

/** What `refresh()` achieved: new tokens, the session is over, or the server was unreachable. */
export type RefreshOutcome = 'refreshed' | 'expired' | 'unavailable';

/** Exchanges a refresh token for new tokens (`POST /auth/refresh`, without a bearer token). */
export type SessionRefresher = (refreshToken: string) => Promise<AuthResponse>;

/**
 * Holds the tokens in memory (for synchronous header injection) and persists them through a
 * TokenStorage. On a 401 the API client asks `refresh()` for new tokens and retries once; only
 * when that fails does the session end (`expire()`).
 */
export interface AuthSession {
  getToken(): string | null;
  getRefreshToken(): string | null;
  restore(): Promise<string | null>;
  start(response: Pick<AuthResponse, 'accessToken' | 'refreshToken'>): Promise<void>;
  end(): Promise<void>;
  /** Fire-and-forget variant of `end()` for the 401 handler. */
  expire(): void;
  /** One refresh at a time; concurrent callers share it. */
  refresh(): Promise<RefreshOutcome>;
  setRefresher(refresher: SessionRefresher): void;
  subscribe(listener: SessionListener): () => void;
}

export function createAuthSession(storage: TokenStorage): AuthSession {
  let credentials: StoredCredentials | null = null;
  let refresher: SessionRefresher | null = null;
  let inflight: Promise<RefreshOutcome> | null = null;
  const listeners = new Set<SessionListener>();
  const notify = () => {
    for (const listener of listeners) {
      listener(credentials?.accessToken ?? null);
    }
  };

  const end = async () => {
    const hadToken = credentials !== null;
    credentials = null;
    try {
      await storage.clear();
    } finally {
      if (hadToken) {
        notify();
      }
    }
  };

  const doRefresh = async (): Promise<RefreshOutcome> => {
    // Another tab of the web app shares the storage and may have rotated the tokens already:
    // presenting the spent refresh token would end the whole sign-in (reuse detection).
    const stored = await storage.get();
    if (stored === null) {
      credentials = null;
      return 'expired';
    }
    if (stored.refreshToken !== credentials?.refreshToken) {
      credentials = stored;
      return 'refreshed';
    }
    const refreshToken = stored.refreshToken;
    if (refreshToken === null || refresher === null) {
      return 'expired';
    }
    try {
      const response = await refresher(refreshToken);
      credentials = { accessToken: response.accessToken, refreshToken: response.refreshToken };
      await storage.set(credentials);
      return 'refreshed';
    } catch (error) {
      // 401/400 from the server: the sign-in is over. Anything else (offline, timeout, 5xx): try later.
      return isApiError(error) && (error.status === 401 || error.status === 400) ? 'expired' : 'unavailable';
    }
  };

  /** Tabs of the web app refresh one after another (Web Locks); native apps have one instance anyway. */
  const exclusive = (task: () => Promise<RefreshOutcome>): Promise<RefreshOutcome> => {
    const locks = (globalThis as { navigator?: { locks?: { request: (name: string, callback: () => Promise<RefreshOutcome>) => Promise<RefreshOutcome> } } })
      .navigator?.locks;
    return locks !== undefined ? locks.request('dukkan-session-refresh', task) : task();
  };

  return {
    getToken: () => credentials?.accessToken ?? null,
    getRefreshToken: () => credentials?.refreshToken ?? null,
    async restore() {
      credentials = await storage.get();
      return credentials?.accessToken ?? null;
    },
    async start(response) {
      credentials = { accessToken: response.accessToken, refreshToken: response.refreshToken };
      await storage.set(credentials);
      notify();
    },
    end,
    expire() {
      end().catch(() => undefined);
    },
    refresh() {
      inflight ??= exclusive(doRefresh).finally(() => {
        inflight = null;
      });
      return inflight;
    },
    setRefresher(next) {
      refresher = next;
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
