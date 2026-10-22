import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Small string key/value store for preferences (language, chosen place). Not for secrets:
 * the access token lives in the keychain (TokenStorage). Failures are swallowed so a broken
 * store never blocks the app; the preference then simply isn't remembered.
 */
export interface KeyValueStorage {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

export const STORAGE_KEYS = {
  language: 'dukkan.language',
  place: 'dukkan.place',
  visitedMarkets: 'dukkan.visitedMarkets',
} as const;

export function createAsyncKeyValueStorage(): KeyValueStorage {
  return {
    async get(key) {
      try {
        return await AsyncStorage.getItem(key);
      } catch {
        return null;
      }
    },
    async set(key, value) {
      try {
        await AsyncStorage.setItem(key, value);
      } catch {
        // Preference not persisted; keep running.
      }
    },
    async remove(key) {
      try {
        await AsyncStorage.removeItem(key);
      } catch {
        // ignore
      }
    },
  };
}

export function createMemoryKeyValueStorage(initial: Record<string, string> = {}): KeyValueStorage {
  const data = new Map(Object.entries(initial));
  return {
    get: (key) => Promise.resolve(data.get(key) ?? null),
    set: (key, value) => {
      data.set(key, value);
      return Promise.resolve();
    },
    remove: (key) => {
      data.delete(key);
      return Promise.resolve();
    },
  };
}
