/** Persistence for the access token. Implementations must never log the token. */
export interface TokenStorage {
  getToken(): Promise<string | null>;
  setToken(token: string): Promise<void>;
  clearToken(): Promise<void>;
}

export function createMemoryTokenStorage(initial: string | null = null): TokenStorage {
  let token = initial;
  return {
    getToken: () => Promise.resolve(token),
    setToken: (next) => {
      token = next;
      return Promise.resolve();
    },
    clearToken: () => {
      token = null;
      return Promise.resolve();
    },
  };
}
