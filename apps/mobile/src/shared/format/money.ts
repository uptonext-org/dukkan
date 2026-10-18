import type { Money } from '../api/types';

const LOCALE = 'de-DE';

const currencyFormatters = new Map<string, Intl.NumberFormat>();
const decimalFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

function currencyFormatter(currency: string): Intl.NumberFormat {
  let formatter = currencyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat(LOCALE, { style: 'currency', currency });
    currencyFormatters.set(currency, formatter);
  }
  return formatter;
}

/** Formats server-provided cents for display, e.g. `{249, EUR}` -> `2,49 €`. Never used for arithmetic. */
export function formatMoney(money: Money): string {
  return currencyFormatter(money.currency).format(money.amountCents / 100);
}

const SPOKEN_CURRENCY: Record<string, string> = { EUR: 'Euro' };

/** Screen-reader friendly variant, e.g. `2,49 Euro`. */
export function formatMoneyForAccessibility(money: Money): string {
  const unit = SPOKEN_CURRENCY[money.currency] ?? money.currency;
  return `${decimalFormatter.format(money.amountCents / 100)} ${unit}`;
}

const UNIT_LABEL: Record<string, string> = { kg: 'kg', l: 'l', piece: 'piece' };

export function unitLabel(unit: string): string {
  return UNIT_LABEL[unit] ?? unit;
}
