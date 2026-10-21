import type {
  FulfilmentType,
  ListMarketProductsParams,
  ListMarketsParams,
  OrderStatus,
  SearchCatalogParams,
  SearchProductsParams,
} from './types';

/**
 * React Query keys: `[domain, language, ...]`. The language is part of every key for localised
 * content, so switching language refetches. Invalidate by domain prefix, e.g. `['carts']`.
 */
export const queryKeys = {
  me: ['me'] as const,
  marketTypes: (lang: string) => ['marketTypes', lang] as const,
  cuisines: (lang: string) => ['cuisines', lang] as const,
  places: (lang: string, q: string) => ['places', lang, q] as const,
  reverse: (lang: string, lat: number, lng: number) => ['reverse', lang, lat, lng] as const,
  markets: (lang: string, params: Omit<ListMarketsParams, 'page'>) => ['markets', lang, params] as const,
  market: (lang: string, marketId: string) => ['market', lang, marketId] as const,
  marketHome: (lang: string, marketId: string) => ['marketHome', lang, marketId] as const,
  categories: (lang: string, marketId: string) => ['categories', lang, marketId] as const,
  allCategories: (lang: string) => ['allCategories', lang] as const,
  products: (lang: string, marketId: string, params: Omit<ListMarketProductsParams, 'page'>) =>
    ['products', lang, marketId, params] as const,
  suggestions: (lang: string, marketId: string, q: string) => ['suggestions', lang, marketId, q] as const,
  product: (lang: string, productId: string) => ['product', lang, productId] as const,
  offers: (lang: string, params: Omit<SearchProductsParams, 'page'>) => ['offers', lang, params] as const,
  favoriteMarkets: (lang: string) => ['favoriteMarkets', lang] as const,
  marketFavorites: (lang: string, marketId: string) => ['marketFavorites', lang, marketId] as const,
  carts: (lang: string) => ['carts', lang] as const,
  cart: (lang: string, marketId: string) => ['cart', lang, marketId] as const,
  slots: (marketId: string, type: FulfilmentType, addressId: string | undefined) =>
    ['slots', marketId, type, addressId ?? null] as const,
  addresses: ['addresses'] as const,
  orders: (lang: string) => ['orders', lang] as const,
  order: (lang: string, orderId: string) => ['order', lang, orderId] as const,
  myMarkets: (lang: string) => ['myMarkets', lang] as const,
  catalog: (lang: string, params: Omit<SearchCatalogParams, 'page'>) => ['catalog', lang, params] as const,
  listings: (lang: string, marketId: string, q: string) => ['listings', lang, marketId, q] as const,
  myPromotions: (lang: string, marketId: string) => ['myPromotions', lang, marketId] as const,
  marketOrders: (lang: string, marketId: string, status: OrderStatus | undefined) =>
    ['marketOrders', lang, marketId, status ?? null] as const,
};
