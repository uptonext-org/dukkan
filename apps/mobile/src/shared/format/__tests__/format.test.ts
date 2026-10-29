import { createI18n } from '../../i18n';
import { forSpeech, isolate, isolateValue, LRI, FSI, PDI } from '../bidi';
import { needsOrganicPrefix, withOrganicPrefix } from '../text';
import { formatDateTime, formatShortDate, formatTimeRange, isoWeekday } from '../dates';
import { centsToInput, formatAmount, formatMoney, formatMoneyForAccessibility, formatMoneyShort, latinDigits,
  parseMoneyInput, splitMoney } from '../money';
import { formatDistance, formatPercent } from '../numbers';
import { ALLERGEN_CODES, allergenLabel, isAllergenCode } from '../allergens';

const plain = (s: string) => forSpeech(s).split(String.fromCharCode(160)).join(' ');

describe('de-DE formatting in every language (decision 3)', () => {
  it('formats money, distance and times with Latin digits and German separators', () => {
    expect(plain(formatMoney({ amountCents: 249, currency: 'EUR' }))).toBe('2,49 €');
    expect(plain(formatMoney({ amountCents: 123456, currency: 'EUR' }))).toBe('1.234,56 €');
    expect(plain(formatMoneyShort({ amountCents: 1500, currency: 'EUR' }))).toBe('15 €');
    expect(plain(formatMoneyShort({ amountCents: 299, currency: 'EUR' }))).toBe('2,99 €');
    expect(formatAmount({ amountCents: 1079, currency: 'EUR' })).toBe('10,79');
    expect(formatMoneyForAccessibility({ amountCents: 1079, currency: 'EUR' })).toBe('10,79 Euro');
    expect(formatDistance(1234)).toBe('1,2 km');
    expect(formatDistance(845)).toBe('850 m');
    expect(formatPercent(20)).toBe('20 %');
    expect(formatTimeRange('2026-10-21T08:00:00+02:00', '2026-10-21T09:00:00+02:00')).toBe('08:00–09:00');
    expect(formatShortDate('2026-10-21T08:00:00+02:00')).toBe('21.10.');
    expect(formatDateTime('2026-10-21T08:00:00+02:00')).toBe('21.10.2026, 08:00');
    expect(isoWeekday('2026-10-21T08:00:00+02:00')).toBe(3);
  });

  it('keeps de-DE numbers inside Arabic sentences', () => {
    const i18n = createI18n('ar');
    const text = plain(i18n.t('market.minimumOrder', { amount: formatMoneyShort({ amountCents: 1500, currency: 'EUR' }) }));
    expect(text).toBe('الحد الأدنى للطلب 15 €');
    const fee = plain(i18n.t('market.deliveryFee', { amount: formatMoney({ amountCents: 299, currency: 'EUR' }) }));
    expect(fee).toContain('2,99 €');
    // No Eastern Arabic digits (U+0660–U+0669) anywhere.
    expect(/[٠-٩]/.test(text + fee)).toBe(false);
  });

  it('splits cents for superscript and parses input', () => {
    expect(splitMoney({ amountCents: 214, currency: 'EUR' })).toEqual({ units: '2', cents: '14', negative: false });
    expect(splitMoney({ amountCents: 5, currency: 'EUR' })).toEqual({ units: '0', cents: '05', negative: false });
    expect(parseMoneyInput('2,49')).toBe(249);
    expect(parseMoneyInput(' 2.5 € ')).toBe(250);
    expect(parseMoneyInput('2,499')).toBeNull();
    expect(parseMoneyInput('abc')).toBeNull();
    expect(centsToInput(123456)).toBe('1234,56');
  });
});

describe('bidi isolation of values in sentences (right-to-left.md › Numbers)', () => {
  const money = (cents: number) => formatMoney({ amountCents: cents, currency: 'EUR' });

  it('wraps interpolated money, dates and counts in a left-to-right isolate in every language', () => {
    const ar = createI18n('ar');
    const text = ar.t('market.deliveryFee', { amount: money(290) });
    // "2,90 €" stays one LTR run, so Arabic does not show it as "€ 2,90".
    expect(text).toContain(`${LRI}${money(290)}${PDI}`);
    const de = createI18n('de');
    expect(de.t('cart.orderCta', { total: money(2340) })).toBe(`Bestellen · ${LRI}${money(2340)}${PDI}`);
    expect(de.t('market.offers', { count: 3 })).toBe(`${LRI}3${PDI} Angebote`);
  });

  it('leaves words alone and uses a first-strong isolate for values with Arabic letters', () => {
    expect(isolateValue('Anatolia Markt')).toBe('Anatolia Markt');
    expect(isolateValue('21.10.')).toBe(`${LRI}21.10.${PDI}`);
    const unitPrice = `${isolate(money(698))}/كغ`;
    expect(isolateValue(unitPrice)).toBe(`${FSI}${unitPrice}${PDI}`);
  });

  it('keeps standalone prices free of marks and strips them for screen readers', () => {
    expect(money(249)).not.toMatch(/[\u2066-\u2069]/);
    const label = createI18n('de').t('market.tabs.cartWithTotal', { amount: '10,79 Euro' });
    expect(label).not.toBe('Warenkorb, 10,79 Euro');
    expect(forSpeech(label)).toBe('Warenkorb, 10,79 Euro');
  });
});

describe('organic prefix', () => {
  it('is shown only when the localised name does not contain the localised word', () => {
    expect(needsOrganicPrefix('Vollmilch', 'Bio')).toBe(true);
    expect(needsOrganicPrefix('Bio-Vollmilch', 'Bio')).toBe(false);
    expect(needsOrganicPrefix('BIO Eier', 'Bio')).toBe(false);
    expect(needsOrganicPrefix('Organic whole milk', 'Organic')).toBe(false);
    expect(needsOrganicPrefix('Whole milk', 'Organic')).toBe(true);
    // Arabic, diacritic-insensitive: "عُضْوِيّ" with harakat still matches "عضوي".
    expect(needsOrganicPrefix('حليب عُضْوِيّ كامل الدسم', 'عضوي')).toBe(false);
    expect(needsOrganicPrefix('حليب كامل الدسم', 'عضوي')).toBe(true);
    expect(withOrganicPrefix('Bio-Vollmilch', true, 'Bio')).toBe('Bio-Vollmilch');
    expect(withOrganicPrefix('Vollmilch', true, 'Bio')).toBe('Bio Vollmilch');
    expect(withOrganicPrefix('Vollmilch', false, 'Bio')).toBe('Vollmilch');
  });
});

describe('allergens', () => {
  it('names the 14 EU allergens in every language and keeps older free-text values', () => {
    const de = createI18n('de');
    const ar = createI18n('ar');
    expect(ALLERGEN_CODES).toHaveLength(14);
    expect(ALLERGEN_CODES.map((code) => allergenLabel(de.t, code))).not.toContain(expect.stringMatching(/^allergen\./));
    expect(allergenLabel(de.t, 'milk')).toBe('Milch');
    expect(allergenLabel(ar.t, 'sesame')).toBe('السمسم');
    expect(allergenLabel(de.t, 'Senf')).toBe('Senf');
    expect(isAllergenCode('nuts')).toBe(true);
    expect(isAllergenCode('glitter')).toBe(false);
  });

  it('reads prices typed with Arabic-Indic digits', () => {
    expect(parseMoneyInput('٢٫٤٩')).toBe(249);
    expect(parseMoneyInput('۳,۵')).toBe(350);
    expect(latinDigits('٠٨:٣٠')).toBe('08:30');
  });
});
