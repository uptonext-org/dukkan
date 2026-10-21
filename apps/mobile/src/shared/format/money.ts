import type { Money } from '../api/types';

/**
 * Numbers and money always use de-DE with Latin digits, in every UI language (decision 3).
 * Formatting only: the app never computes prices.
 */
export const NUMBER_LOCALE = 'de-DE';

const currencyFormatters = new Map<string, Intl.NumberFormat>();
const decimal2 = new Intl.NumberFormat(NUMBER_LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const integer = new Intl.NumberFormat(NUMBER_LOCALE, { maximumFractionDigits: 0 });

function currencyFormatter(currency: string, whole: boolean): Intl.NumberFormat {
  const key = `${currency}:${String(whole)}`;
  let formatter = currencyFormatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(NUMBER_LOCALE, {
      style: 'currency',
      currency,
      ...(whole ? { minimumFractionDigits: 0, maximumFractionDigits: 0 } : {}),
    });
    currencyFormatters.set(key, formatter);
  }
  return formatter;
}

/** `{249, EUR}` → `2,49 €`. */
export function formatMoney(money: Money): string {
  return currencyFormatter(money.currency, false).format(money.amountCents / 100);
}

/** Like formatMoney but drops `,00`: `{1500, EUR}` → `15 €`, `{299, EUR}` → `2,99 €`. */
export function formatMoneyShort(money: Money): string {
  const whole = money.amountCents % 100 === 0;
  return currencyFormatter(money.currency, whole).format(money.amountCents / 100);
}

/** Amount without currency sign: `{1079, EUR}` → `10,79` (tab badge). */
export function formatAmount(money: Money): string {
  return decimal2.format(money.amountCents / 100);
}

/** Integer and cents parts for superscript display: `{214}` → `{ units: '2', cents: '14' }`. */
export function splitMoney(money: Money): { units: string; cents: string; negative: boolean } {
  const negative = money.amountCents < 0;
  const abs = Math.abs(money.amountCents);
  return {
    units: integer.format(Math.floor(abs / 100)),
    cents: String(abs % 100).padStart(2, '0'),
    negative,
  };
}

const SPOKEN_CURRENCY: Record<string, string> = { EUR: 'Euro' };

/** Screen-reader text, e.g. `2,49 Euro` (the currency name is the same in de/en/ar transliteration). */
export function formatMoneyForAccessibility(money: Money): string {
  const unit = SPOKEN_CURRENCY[money.currency] ?? money.currency;
  return `${decimal2.format(money.amountCents / 100)} ${unit}`;
}

/** Parses user input like `2,49`, `2.49` or `2` into cents; null when invalid. */
export function parseMoneyInput(input: string): number | null {
  const normalized = input.trim().replace(/\s|€/g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    return null;
  }
  return Math.round(Number(normalized) * 100);
}

/** Cents → input text `2,49`. */
export function centsToInput(cents: number): string {
  return decimal2.format(cents / 100).replace(/\./g, '');
}
