import 'i18next';

import type de from '../../i18n/locales/de.json';

/** Typed keys: `t('some.missing.key')` is a compile error. German is the reference locale. */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: { translation: typeof de };
  }
}
