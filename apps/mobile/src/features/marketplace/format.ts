import type { TFunction } from 'i18next';

import type { MarketSummary } from '../../shared/api';
import { formatDistance, formatMoneyShort, formatTime, isolate } from '../../shared/format';

const BERLIN_TIME = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Berlin', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

/**
 * Closing time of today's interval that is open at `now` (Berlin time), from `todayHours` like
 * "08:00–13:00, 14:00–18:30" or "20:00–02:00". Undefined when none contains it (e.g. after midnight
 * in yesterday's overnight interval, which today's hours do not list).
 */
export function closesAt(todayHours: string | null | undefined, now: Date): string | undefined {
  const time = BERLIN_TIME.format(now);
  for (const interval of (todayHours ?? '').split(',')) {
    const [opens, closes] = interval.split(/[–-]/).map((part) => part.trim());
    if (opens === undefined || closes === undefined || !/^\d{2}:\d{2}$/.test(opens) || !/^\d{2}:\d{2}$/.test(closes)) {
      continue;
    }
    const overnight = closes <= opens;
    if (overnight ? time >= opens : time >= opens && time < closes) {
      return closes;
    }
  }
  return undefined;
}

/** "Geöffnet bis 20:00" / "Geschlossen · öffnet 08:00" / "Geschlossen". */
export function openingLabel(t: TFunction, market: Pick<MarketSummary, 'openNow' | 'todayHours' | 'nextOpeningAt'>, now: Date = new Date()): string {
  if (market.openNow) {
    const closes = closesAt(market.todayHours, now);
    return closes !== undefined ? t('market.openUntil', { time: closes }) : t('market.open');
  }
  if (market.nextOpeningAt != null) {
    return t('market.closedOpensAt', { time: formatTime(market.nextOpeningAt) });
  }
  return t('market.closed');
}

/** "Supermarkt · Türkisch, Arabisch". */
export function typeAndCuisine(market: Pick<MarketSummary, 'type' | 'cuisines'>): string {
  const cuisines = market.cuisines.map((c) => c.name).join(', ');
  return cuisines !== '' ? `${market.type.name} · ${cuisines}` : market.type.name;
}

/** "1,2 km", isolated so it keeps its order when it leads an Arabic line ("190 m · مفتوح حتى 20:00"). */
export function distanceLabel(market: Pick<MarketSummary, 'distanceMeters'>): string | undefined {
  return market.distanceMeters != null ? isolate(formatDistance(market.distanceMeters)) : undefined;
}

/** "Lieferung 2,99 €" / "Lieferung gratis"; undefined without delivery. */
export function deliveryFeeLabel(t: TFunction, market: Pick<MarketSummary, 'fulfilment'>): string | undefined {
  const { fulfilment } = market;
  if (!fulfilment.delivery) {
    return undefined;
  }
  if (fulfilment.deliveryFee == null || fulfilment.deliveryFee.amountCents === 0) {
    return t('market.deliveryFree');
  }
  return t('market.deliveryFee', { amount: formatMoneyShort(fulfilment.deliveryFee) });
}

export function minimumOrderLabel(t: TFunction, market: Pick<MarketSummary, 'fulfilment'>): string | undefined {
  const min = market.fulfilment.minimumOrderValue;
  return min.amountCents > 0 ? t('market.minimumOrder', { amount: formatMoneyShort(min) }) : undefined;
}
