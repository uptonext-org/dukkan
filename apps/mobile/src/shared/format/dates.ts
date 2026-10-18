/** Delivery times are shown in the hub's timezone regardless of the device timezone. */
export const DELIVERY_TIME_ZONE = 'Europe/Berlin';
const LOCALE = 'de-DE';

const dayKeyFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: DELIVERY_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const dayLabelFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: DELIVERY_TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});
const timeFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: DELIVERY_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const dateTimeFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: DELIVERY_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const dateFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: DELIVERY_TIME_ZONE,
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

export function formatDayLabel(iso: string): string {
  return dayLabelFormatter.format(new Date(iso));
}

export function formatTime(iso: string): string {
  return timeFormatter.format(new Date(iso));
}

export function formatTimeRange(startIso: string, endIso: string): string {
  return `${formatTime(startIso)}–${formatTime(endIso)}`;
}

export function formatDateTime(iso: string): string {
  return dateTimeFormatter.format(new Date(iso));
}

export function formatDate(iso: string): string {
  return dateFormatter.format(new Date(iso));
}
