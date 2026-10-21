import { NUMBER_LOCALE } from './money';

/** Market times are Europe/Berlin regardless of the device time zone (contract: opening hours). */
export const MARKET_TIME_ZONE = 'Europe/Berlin';

const dayKeyFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: MARKET_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const weekdayFormatter = new Intl.DateTimeFormat('en-US', { timeZone: MARKET_TIME_ZONE, weekday: 'short' });
const timeFormatter = new Intl.DateTimeFormat(NUMBER_LOCALE, {
  timeZone: MARKET_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const shortDateFormatter = new Intl.DateTimeFormat(NUMBER_LOCALE, {
  timeZone: MARKET_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
});
const dateFormatter = new Intl.DateTimeFormat(NUMBER_LOCALE, {
  timeZone: MARKET_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

/** Calendar day in Europe/Berlin as `YYYY-MM-DD`. */
export function dayKey(iso: string): string {
  const parts = dayKeyFormatter.formatToParts(new Date(iso));
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/** ISO weekday (1 = Monday) in Europe/Berlin; names are translated by the caller (`weekday.<n>`). */
export function isoWeekday(iso: string): 1 | 2 | 3 | 4 | 5 | 6 | 7 {
  const index = WEEKDAYS.indexOf(weekdayFormatter.format(new Date(iso)));
  return (index < 0 ? 1 : index + 1) as 1 | 2 | 3 | 4 | 5 | 6 | 7;
}

/** `20.10.` */
export function formatShortDate(iso: string): string {
  return shortDateFormatter.format(new Date(iso));
}

/** `20.10.2026` */
export function formatDate(iso: string): string {
  return dateFormatter.format(new Date(iso));
}

/** `08:00` */
export function formatTime(iso: string): string {
  return timeFormatter.format(new Date(iso));
}

/** `08:00–09:00` */
export function formatTimeRange(startIso: string, endIso: string): string {
  return `${formatTime(startIso)}–${formatTime(endIso)}`;
}

/** `20.10.2026, 08:00` */
export function formatDateTime(iso: string): string {
  return `${formatDate(iso)}, ${formatTime(iso)}`;
}
