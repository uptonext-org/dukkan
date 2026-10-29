import { createContext, use, useCallback, useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import type { i18n as I18n } from 'i18next';
import { I18nextProvider } from 'react-i18next';
import { I18nManager, Platform } from 'react-native';

import type { LanguageStore } from './languageStore';
import { directionOf, type Direction, type Language } from './languages';

interface LanguageContextValue {
  language: Language;
  direction: Direction;
  isRTL: boolean;
  /**
   * Switches language instantly (strings, fonts, layout direction via the root `direction` style).
   * Returns true when native-only parts (system back-swipe direction, native alerts) only follow
   * after an app restart, because I18nManager.forceRTL takes effect on the next launch.
   */
  setLanguage: (language: Language) => Promise<boolean>;
}

const LanguageContext = createContext<LanguageContextValue | null>(null);

export function LanguageProvider({
  store,
  i18n,
  children,
}: {
  store: LanguageStore;
  i18n: I18n;
  children: ReactNode;
}) {
  const language = useSyncExternalStore(store.subscribe, store.get, store.get);
  const direction = directionOf(language);

  useEffect(() => {
    if (i18n.language !== language) {
      void i18n.changeLanguage(language);
    }
  }, [i18n, language]);

  const setLanguage = useCallback(
    async (next: Language) => {
      const rtl = directionOf(next) === 'rtl';
      await i18n.changeLanguage(next);
      await store.set(next);
      // Persist the direction for native components on the next launch.
      I18nManager.allowRTL(rtl);
      I18nManager.forceRTL(rtl);
      // A restart is needed only for native layout; the browser switches direction right away.
      return Platform.OS !== 'web' && I18nManager.isRTL !== rtl;
    },
    [i18n, store],
  );

  const value = useMemo(
    () => ({ language, direction, isRTL: direction === 'rtl', setLanguage }),
    [language, direction, setLanguage],
  );
  return (
    <I18nextProvider i18n={i18n}>
      <LanguageContext value={value}>{children}</LanguageContext>
    </I18nextProvider>
  );
}

export function useLanguage(): LanguageContextValue {
  const value = use(LanguageContext);
  if (value === null) {
    throw new Error('useLanguage must be used inside <LanguageProvider>.');
  }
  return value;
}
