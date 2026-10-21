import { createI18n } from '../../i18n';
import { formatDateTime, formatShortDate, formatTimeRange, isoWeekday } from '../dates';
import { centsToInput, formatAmount, formatMoney, formatMoneyForAccessibility, formatMoneyShort, parseMoneyInput, splitMoney } from '../money';
import { formatDistance, formatPercent } from '../numbers';

const plain = (s: string) => s.split(String.fromCharCode(160)).join(' ');

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
