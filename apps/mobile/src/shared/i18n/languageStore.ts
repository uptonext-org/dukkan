import { STORAGE_KEYS, type KeyValueStorage } from '../storage';
import { FALLBACK_LANGUAGE, isLanguage, LANGUAGES, type Language } from './languages';

export type LanguageListener = (language: Language) => void;

/**
 * Holds the current UI language outside React so the ApiClient can read it synchronously for
 * `Accept-Language`. React subscribes through `useSyncExternalStore` (LanguageProvider).
 */
export interface LanguageStore {
  get: () => Language;
  /** Loads the persisted choice, otherwise the best device language, otherwise `de`. */
  restore: () => Promise<Language>;
  set: (language: Language) => Promise<void>;
  subscribe: (listener: LanguageListener) => () => void;
}

/** First device locale we support, e.g. `['ar-EG', 'en-US']` → `ar`. Unsupported → `de`. */
export function pickDeviceLanguage(deviceLanguageTags: readonly string[]): Language {
  for (const tag of deviceLanguageTags) {
    const code = tag.toLowerCase().split(/[-_]/)[0];
    if (isLanguage(code)) {
      return code;
    }
  }
  return FALLBACK_LANGUAGE;
}

export function createLanguageStore(
  storage: KeyValueStorage,
  deviceLanguageTags: () => readonly string[],
  initial: Language = pickDeviceLanguage(safe(deviceLanguageTags)),
): LanguageStore {
  let language: Language = initial;
  const listeners = new Set<LanguageListener>();
  const notify = () => {
    for (const listener of listeners) {
      listener(language);
    }
  };
  return {
    get: () => language,
    async restore() {
      const stored = await storage.get(STORAGE_KEYS.language);
      const next = isLanguage(stored) ? stored : pickDeviceLanguage(safe(deviceLanguageTags));
      if (next !== language) {
        language = next;
        notify();
      }
      return language;
    },
    async set(next) {
      if (!LANGUAGES.some((l) => l.code === next)) {
        return;
      }
      language = next;
      notify();
      await storage.set(STORAGE_KEYS.language, next);
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

function safe(fn: () => readonly string[]): readonly string[] {
  try {
    return fn();
  } catch {
    return [];
  }
}
