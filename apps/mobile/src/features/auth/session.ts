import type { TokenStorage } from '../../shared/storage';

export type SessionListener = (token: string | null) => void;

/**
 * Holds the access token in memory (for synchronous header injection) and persists it
 * through a TokenStorage. The API client calls `expire()` on 401 responses.
 */
export interface AuthSession {
  getToken(): string | null;
  restore(): Promise<string | null>;
  start(token: string): Promise<void>;
  end(): Promise<void>;
  /** Fire-and-forget variant of `end()` for the 401 handler. */
  expire(): void;
  subscribe(listener: SessionListener): () => void;
}

export function createAuthSession(storage: TokenStorage): AuthSession {
  let token: string | null = null;
  const listeners = new Set<SessionListener>();
  const notify = () => {
    for (const listener of listeners) {
      listener(token);
    }
  };

  const end = async () => {
    const hadToken = token !== null;
    token = null;
    try {
      await storage.clearToken();
    } finally {
      if (hadToken) {
        notify();
      }
    }
  };

  return {
    getToken: () => token,
    async restore() {
      token = await storage.getToken();
      return token;
    },
    async start(next) {
      token = next;
      await storage.setToken(next);
      notify();
    },
    end,
    expire() {
      end().catch(() => undefined);
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
