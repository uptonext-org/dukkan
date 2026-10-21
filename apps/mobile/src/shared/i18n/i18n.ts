import i18next, { type i18n as I18n } from 'i18next';
import { initReactI18next } from 'react-i18next';

import ar from '../../i18n/locales/ar.json';
import de from '../../i18n/locales/de.json';
import en from '../../i18n/locales/en.json';
import { FALLBACK_LANGUAGE, type Language } from './languages';

export const resources = {
  de: { translation: de },
  en: { translation: en },
  ar: { translation: ar },
} as const;

/** A fresh i18next instance (one per app, one per test). Resources are bundled, so init is synchronous. */
export function createI18n(language: Language): I18n {
  const instance = i18next.createInstance();
  void instance.use(initReactI18next).init({
    resources,
    lng: language,
    fallbackLng: FALLBACK_LANGUAGE,
    supportedLngs: Object.keys(resources),
    interpolation: { escapeValue: false },
    initAsync: false,
    returnNull: false,
  });
  return instance;
}
