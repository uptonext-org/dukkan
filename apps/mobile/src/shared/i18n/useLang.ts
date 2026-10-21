import { useLanguage } from './LanguageProvider';
import type { Language } from './languages';

/** Current language for React Query keys. */
export function useLang(): Language {
  return useLanguage().language;
}
