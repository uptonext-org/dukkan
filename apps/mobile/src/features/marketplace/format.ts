import type { TFunction } from 'i18next';

import type { MarketSummary } from '../../shared/api';
import { formatDistance, formatMoneyShort, formatTime, isolate } from '../../shared/format';

/** "Geöffnet bis 20:00" / "Geschlossen · öffnet 08:00" / "Geschlossen". */
export function openingLabel(t: TFunction, market: Pick<MarketSummary, 'openNow' | 'todayHours' | 'nextOpeningAt'>): string {
  if (market.openNow) {
    const closes = market.todayHours?.split(/[–-]/).pop()?.trim();
    return closes !== undefined && closes !== '' ? t('market.openUntil', { time: closes }) : t('market.open');
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
