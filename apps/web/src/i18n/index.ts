import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import ar from './ar.json';
import de from './de.json';
import en from './en.json';

export const LANGUAGES = [
  { code: 'de', nativeName: 'Deutsch', direction: 'ltr' },
  { code: 'en', nativeName: 'English', direction: 'ltr' },
  { code: 'ar', nativeName: 'العربية', direction: 'rtl' },
] as const;

export type Language = (typeof LANGUAGES)[number]['code'];

export function isLanguage(code: string | null | undefined): code is Language {
  return LANGUAGES.some((l) => l.code === code);
}

function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem('dukkan.language');
    if (isLanguage(saved)) {
      return saved;
    }
  } catch {
    // Storage blocked: fall back to the browser language.
  }
  const browser = typeof navigator === 'undefined' ? 'de' : navigator.language.slice(0, 2);
  return isLanguage(browser) ? browser : 'de';
}

/** Sets the language, the document's lang and dir (RTL for Arabic), and remembers the choice. */
export function applyLanguage(language: Language) {
  void i18n.changeLanguage(language);
  document.documentElement.lang = language;
  document.documentElement.dir = LANGUAGES.find((l) => l.code === language)?.direction ?? 'ltr';
  try {
    localStorage.setItem('dukkan.language', language);
  } catch {
    // Not remembered; harmless.
  }
}

export function initI18n(language: Language = initialLanguage()) {
  if (!i18n.isInitialized) {
    void i18n.use(initReactI18next).init({
      resources: { de: { translation: de }, en: { translation: en }, ar: { translation: ar } },
      lng: language,
      fallbackLng: 'de',
      interpolation: { escapeValue: false },
      returnNull: false,
    });
  }
  applyLanguage(language);
  return i18n;
}

export default i18n;
