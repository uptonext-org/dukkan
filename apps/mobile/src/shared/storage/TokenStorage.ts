/** The signed-in session: a short-lived access token and the rotating refresh token. */
export interface StoredCredentials {
  accessToken: string;
  /** Null for sessions stored by app versions before refresh tokens (B-39). */
  refreshToken: string | null;
}

/** Persistence for the session tokens. Implementations must never log them. */
export interface TokenStorage {
  get(): Promise<StoredCredentials | null>;
  set(credentials: StoredCredentials): Promise<void>;
  clear(): Promise<void>;
}

/** Reads what the keychain holds: JSON credentials, or a bare JWT from older app versions. */
export function parseStoredCredentials(raw: string): StoredCredentials | null {
  if (raw.startsWith('{')) {
    try {
      const value = JSON.parse(raw) as Partial<StoredCredentials>;
      return typeof value.accessToken === 'string'
        ? { accessToken: value.accessToken, refreshToken: typeof value.refreshToken === 'string' ? value.refreshToken : null }
        : null;
    } catch {
      return null;
    }
  }
  return raw === '' ? null : { accessToken: raw, refreshToken: null };
}

export function createMemoryTokenStorage(initial: StoredCredentials | string | null = null): TokenStorage {
  let credentials: StoredCredentials | null = typeof initial === 'string' ? { accessToken: initial, refreshToken: null } : initial;
  return {
    get: () => Promise.resolve(credentials),
    set: (next) => {
      credentials = next;
      return Promise.resolve();
    },
    clear: () => {
      credentials = null;
      return Promise.resolve();
    },
  };
}
