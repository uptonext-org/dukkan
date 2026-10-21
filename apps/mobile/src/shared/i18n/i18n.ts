import i18next, { type i18n as I18n } from 'i18next';
import { initReactI18next } from 'react-i18next';

import ar from '../../i18n/locales/ar.json';
import de from '../../i18n/locales/de.json';
import en from '../../i18n/locales/en.json';
import { isolateValue } from '../format/bidi';
import { FALLBACK_LANGUAGE, type Language } from './languages';

export const resources = {
  de: { translation: de },
  en: { translation: en },
  ar: { translation: ar },
} as const;

/**
 * A fresh i18next instance (one per app, one per test). Resources are bundled, so init is synchronous.
 *
 * Interpolation does no HTML escaping (React renders text, not markup). Instead the `escape` hook,
 * which i18next applies to every interpolated value, wraps values with digits (money, counts,
 * dates, times) in a Unicode isolate, so "2,90 €" and "21.10." keep their order inside Arabic
 * sentences, in every language (shared/format/bidi.ts). Text that goes to a screen reader is
 * passed through `forSpeech()`, which removes the marks again.
 */
export function createI18n(language: Language): I18n {
  const instance = i18next.createInstance();
  void instance.use(initReactI18next).init({
    resources,
    lng: language,
    fallbackLng: FALLBACK_LANGUAGE,
    supportedLngs: Object.keys(resources),
    interpolation: { escapeValue: true, escape: (value: unknown) => isolateValue(String(value)) },
    initAsync: false,
    returnNull: false,
  });
  return instance;
}
