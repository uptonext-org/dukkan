/** Supported UI languages. Adding one = a new locales/<code>.json plus an entry here. */
export const LANGUAGES = [
  { code: 'de', nativeName: 'Deutsch', direction: 'ltr' },
  { code: 'en', nativeName: 'English', direction: 'ltr' },
  { code: 'ar', nativeName: 'العربية', direction: 'rtl' },
] as const;

export type Language = (typeof LANGUAGES)[number]['code'];
export type Direction = 'ltr' | 'rtl';

export const FALLBACK_LANGUAGE: Language = 'de';

export function isLanguage(value: unknown): value is Language {
  return LANGUAGES.some((l) => l.code === value);
}

export function directionOf(language: Language): Direction {
  return LANGUAGES.find((l) => l.code === language)?.direction ?? 'ltr';
}
