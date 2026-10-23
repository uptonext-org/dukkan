import type { KeyValueStorage } from '../../../mobile/src/shared/storage/KeyValueStorage';
import { parseStoredCredentials, type TokenStorage } from '../../../mobile/src/shared/storage/TokenStorage';

/** Preferences in localStorage; a blocked store (private mode, quota) only loses the preference. */
export function createLocalKeyValueStorage(store: Storage = window.localStorage): KeyValueStorage {
  return {
    get(key) {
      try {
        return Promise.resolve(store.getItem(key));
      } catch {
        return Promise.resolve(null);
      }
    },
    set(key, value) {
      try {
        store.setItem(key, value);
      } catch {
        // Not remembered; keep running.
      }
      return Promise.resolve();
    },
    remove(key) {
      try {
        store.removeItem(key);
      } catch {
        // ignore
      }
      return Promise.resolve();
    },
  };
}

const SESSION_KEY = 'dukkan.session';

/**
 * The session tokens in localStorage, so a signed-in customer stays signed in across tabs and
 * restarts, as in the app. Scripts are restricted by the Content-Security-Policy (deploy/nginx),
 * the access token lives 15 minutes and the refresh token rotates on every use.
 */
export function createLocalTokenStorage(store: Storage = window.localStorage): TokenStorage {
  return {
    get() {
      try {
        const raw = store.getItem(SESSION_KEY);
        return Promise.resolve(raw === null ? null : parseStoredCredentials(raw));
      } catch {
        return Promise.resolve(null);
      }
    },
    set(credentials) {
      store.setItem(SESSION_KEY, JSON.stringify(credentials));
      return Promise.resolve();
    },
    clear() {
      try {
        store.removeItem(SESSION_KEY);
      } catch {
        // ignore
      }
      return Promise.resolve();
    },
  };
}
