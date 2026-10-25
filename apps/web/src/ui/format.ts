import type { Money } from '../api/types';

/** Money in the UI language; Latin digits in Arabic too, as in the app (prices are compared with receipts). */
export function formatMoney(money: Money, language: string): string {
  return new Intl.NumberFormat(language === 'ar' ? 'ar-u-nu-latn' : language, { style: 'currency', currency: money.currency }).format(
    money.amountCents / 100,
  );
}

export function formatSlot(startsAt: string, endsAt: string, language: string): string {
  const locale = language === 'ar' ? 'ar-u-nu-latn' : language;
  const day = new Intl.DateTimeFormat(locale, { weekday: 'short', day: '2-digit', month: '2-digit', timeZone: 'Europe/Berlin' }).format(new Date(startsAt));
  const time = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Berlin' });
  return `${day}, ${time.format(new Date(startsAt))}–${time.format(new Date(endsAt))}`;
}

/** `4,49`, `4.49`, `4,49 €` → 449; null when it is not a price. */
export function parsePrice(text: string): number | null {
  const plain = text.replace(/€|\s/g, '');
  if (!/^\d{1,5}([.,]\d{1,2})?$/.test(plain)) {
    return null;
  }
  const cents = Math.round(Number(plain.replace(',', '.')) * 100);
  return cents > 0 ? cents : null;
}

export function centsToInput(cents: number, language: string): string {
  const text = (cents / 100).toFixed(2);
  return language === 'en' ? text : text.replace('.', ',');
}

/** Date and time in Berlin time, e.g. `20.10.26, 11:30`. */
export function formatDateTime(iso: string, language: string): string {
  const locale = language === 'ar' ? 'ar-u-nu-latn' : language;
  return new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Berlin' }).format(new Date(iso));
}
