import ar from '../../../i18n/locales/ar.json';
import de from '../../../i18n/locales/de.json';
import en from '../../../i18n/locales/en.json';
import { KNOWN_ERROR_CODES } from '../../api/errorMessages';
import { createI18n } from '../i18n';
import { pickDeviceLanguage } from '../languageStore';

type Tree = { [key: string]: string | Tree };

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix === '' ? key : `${prefix}.${key}`;
    if (typeof value === 'string') {
      out[path] = value;
    } else {
      Object.assign(out, flatten(value, path));
    }
  }
  return out;
}

const PLURAL = /_(zero|one|two|few|many|other)$/;
const locales = { de: flatten(de), en: flatten(en), ar: flatten(ar) };
const baseKeys = (keys: string[]) => [...new Set(keys.map((k) => k.replace(PLURAL, '')))].sort();

describe('locale files', () => {
  it('have the same keys in de, en and ar (plural suffixes aside)', () => {
    const reference = baseKeys(Object.keys(locales.de));
    expect(baseKeys(Object.keys(locales.en))).toEqual(reference);
    expect(baseKeys(Object.keys(locales.ar))).toEqual(reference);
  });

  it('have every plural form each language needs (Intl.PluralRules)', () => {
    for (const [lang, entries] of Object.entries(locales)) {
      const needed = new Intl.PluralRules(lang).resolvedOptions().pluralCategories;
      const pluralBases = new Set(Object.keys(entries).filter((k) => PLURAL.test(k)).map((k) => k.replace(PLURAL, '')));
      for (const base of pluralBases) {
        for (const category of needed) {
          expect([lang, `${base}_${category}`, entries[`${base}_${category}`] !== undefined]).toEqual([lang, `${base}_${category}`, true]);
        }
      }
    }
    expect(new Intl.PluralRules('ar').resolvedOptions().pluralCategories).toHaveLength(6);
  });

  it('use the same interpolation variables in every language', () => {
    const vars = (text: string) => [...text.matchAll(/{{(\w+)}}/g)].map((m) => m[1]).sort();
    for (const [key, text] of Object.entries(locales.de)) {
      const base = key.replace(PLURAL, '');
      const forms = (lang: 'en' | 'ar') => Object.entries(locales[lang]).filter(([k]) => k.replace(PLURAL, '') === base);
      for (const lang of ['en', 'ar'] as const) {
        for (const [otherKey, other] of forms(lang)) {
          // Arabic zero/one/two forms may spell the number out instead of {{count}}.
          const ignoreCount = lang === 'ar' && /_(zero|one|two)$/.test(otherKey);
          const expected = vars(text).filter((v) => !(ignoreCount && v === 'count'));
          expect([otherKey, vars(other).filter((v) => !(ignoreCount && v === 'count'))]).toEqual([otherKey, expected]);
        }
      }
    }
  });

  it('translate every API error code the contract defines', () => {
    for (const code of [...KNOWN_ERROR_CODES, 'UNKNOWN']) {
      for (const entries of Object.values(locales)) {
        expect(entries[`errors.${code}`]).toEqual(expect.any(String));
      }
    }
  });

  it('keep Arabic copy free of gendered singular imperatives used in UI buttons', () => {
    // Masculine singular imperatives we deliberately avoid (decision 18: neutral or plural).
    const imperatives = ['اختر ', 'أضف ', 'ابحث ', 'سجّل ', 'اضغط ', 'أدخل '];
    for (const text of Object.values(locales.ar)) {
      for (const word of imperatives) {
        expect(text.startsWith(word)).toBe(false);
      }
    }
  });
});

describe('i18next setup', () => {
  it('pluralises Arabic with six forms and keeps Latin digits', () => {
    const i18n = createI18n('ar');
    expect(i18n.t('market.offers', { count: 1 })).toBe('عرض واحد');
    expect(i18n.t('market.offers', { count: 2 })).toBe('عرضان');
    expect(i18n.t('market.offers', { count: 3 })).toBe('3 عروض');
    expect(i18n.t('market.offers', { count: 11 })).toBe('11 عرضًا');
    expect(i18n.t('market.offers', { count: 100 })).toBe('100 عرض');
  });

  it('falls back to German for unsupported device languages', () => {
    expect(pickDeviceLanguage(['fr-FR', 'it-IT'])).toBe('de');
    expect(pickDeviceLanguage(['ar-EG', 'en-US'])).toBe('ar');
    expect(pickDeviceLanguage(['en_GB'])).toBe('en');
    expect(pickDeviceLanguage([])).toBe('de');
  });
});
