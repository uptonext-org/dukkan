import { NUMBER_LOCALE } from './money';

const oneDecimal = new Intl.NumberFormat(NUMBER_LOCALE, { minimumFractionDigits: 0, maximumFractionDigits: 1 });
const integer = new Intl.NumberFormat(NUMBER_LOCALE, { maximumFractionDigits: 0 });

/** `1234` → `1,2 km`, `10000` → `10 km`, `850` → `850 m` (de-DE, Latin digits). */
export function formatDistance(meters: number): string {
  if (meters < 1000) {
    return `${integer.format(Math.round(meters / 10) * 10)} m`;
  }
  return `${oneDecimal.format(meters / 1000)} km`;
}

export function formatInteger(value: number): string {
  return integer.format(value);
}

/** `20` → `20 %` (German spacing, Latin digits). */
export function formatPercent(value: number): string {
  return `${integer.format(value)} %`;
}
