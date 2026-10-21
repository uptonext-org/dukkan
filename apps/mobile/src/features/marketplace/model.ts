import type { FulfilmentType, ListMarketsParams, MarketSort, MarketSummary } from '../../shared/api';
import type { GeoPoint } from '../../shared/api';

export interface MarketFilters {
  types: string[];
  cuisines: string[];
  sort: MarketSort;
  delivery: boolean;
  pickup: boolean;
  openNow: boolean;
  hasOffers: boolean;
}

export const DEFAULT_FILTERS: MarketFilters = {
  types: [],
  cuisines: [],
  sort: 'distance',
  delivery: false,
  pickup: false,
  openNow: false,
  hasOffers: false,
};

export const PAGE_SIZE = 20;

/**
 * Filters → /markets query. The contract has a single `fulfilment` value: exactly one of
 * delivery/pickup narrows the list, both or neither means "any".
 */
export function buildMarketQuery(filters: MarketFilters, at: GeoPoint): Omit<ListMarketsParams, 'page'> {
  let fulfilment: FulfilmentType | undefined;
  if (filters.delivery !== filters.pickup) {
    fulfilment = filters.delivery ? 'DELIVERY' : 'PICKUP';
  }
  return {
    lat: at.lat,
    lng: at.lng,
    sort: filters.sort,
    size: PAGE_SIZE,
    ...(filters.types.length > 0 ? { types: [...filters.types].sort() } : {}),
    ...(filters.cuisines.length > 0 ? { cuisines: [...filters.cuisines].sort() } : {}),
    ...(fulfilment !== undefined ? { fulfilment } : {}),
    ...(filters.openNow ? { openNow: true } : {}),
    ...(filters.hasOffers ? { hasOffers: true } : {}),
  };
}

/** Number of non-default sheet filters, for the filter button badge. */
export function activeFilterCount(filters: MarketFilters): number {
  return [filters.sort !== 'distance', filters.delivery, filters.pickup, filters.openNow, filters.hasOffers].filter(Boolean).length;
}

export function toggle(list: readonly string[], code: string): string[] {
  return list.includes(code) ? list.filter((c) => c !== code) : [...list, code];
}

/** Map pin label: first letter of the market name. */
export function marketInitial(market: Pick<MarketSummary, 'name'>): string {
  return market.name.trim().charAt(0).toUpperCase() || '•';
}
