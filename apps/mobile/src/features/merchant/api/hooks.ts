import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  flattenPages,
  nextPage,
  queryKeys,
  useApi,
  type CreateListingRequest,
  type MarketProfileRequest,
  type OrderStatus,
  type PromotionRequest,
  type SearchCatalogParams,
  type UpdateListingRequest,
} from '../../../shared/api';
import { useLang } from '../../../shared/i18n';

export function useMyMarkets() {
  const api = useApi();
  const lang = useLang();
  return useQuery({ queryKey: queryKeys.myMarkets(lang), queryFn: ({ signal }) => api.listMyMarkets(signal) });
}

export function useSaveMarket(marketId?: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: MarketProfileRequest) =>
      marketId !== undefined ? api.updateMyMarket(marketId, request) : api.createMyMarket(request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['myMarkets'] });
      void queryClient.invalidateQueries({ queryKey: ['markets'] });
    },
  });
}

export function useCatalogSearch(params: Omit<SearchCatalogParams, 'page'>) {
  const api = useApi();
  const lang = useLang();
  const query = useInfiniteQuery({
    queryKey: queryKeys.catalog(lang, params),
    queryFn: ({ pageParam, signal }) => api.searchSharedCatalog({ ...params, page: pageParam }, signal),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
  return { ...query, products: flattenPages(query.data) };
}

export function useListings(marketId: string, q: string) {
  const api = useApi();
  const lang = useLang();
  const query = useInfiniteQuery({
    queryKey: queryKeys.listings(lang, marketId, q),
    queryFn: ({ pageParam, signal }) =>
      api.listMyListings(marketId, { ...(q !== '' ? { q } : {}), page: pageParam, size: 30 }, signal),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
  return { ...query, listings: flattenPages(query.data) };
}

function useInvalidateListings() {
  const queryClient = useQueryClient();
  return () => {
    for (const key of ['listings', 'catalog', 'products', 'marketHome']) {
      void queryClient.invalidateQueries({ queryKey: [key] });
    }
  };
}

export function useCreateListing(marketId: string) {
  const api = useApi();
  const invalidate = useInvalidateListings();
  return useMutation({
    mutationFn: (request: CreateListingRequest) => api.createListing(marketId, request),
    onSuccess: invalidate,
  });
}

export function useUpdateListing(marketId: string) {
  const api = useApi();
  const invalidate = useInvalidateListings();
  return useMutation({
    mutationFn: ({ productId, request }: { productId: string; request: UpdateListingRequest }) =>
      api.updateListing(marketId, productId, request),
    onSuccess: invalidate,
  });
}

export function useMyPromotions(marketId: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({ queryKey: queryKeys.myPromotions(lang, marketId), queryFn: ({ signal }) => api.listMyPromotions(marketId, signal) });
}

export function useCreatePromotion(marketId: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: PromotionRequest) => api.createPromotion(marketId, request),
    onSuccess: () => {
      for (const key of ['myPromotions', 'listings', 'products', 'marketHome']) {
        void queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}

export function useEndPromotion(marketId: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (promotionId: string) => api.deletePromotion(marketId, promotionId),
    onSuccess: () => {
      for (const key of ['myPromotions', 'listings', 'products', 'marketHome']) {
        void queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}

export function useMarketOrders(marketId: string, status: OrderStatus | undefined) {
  const api = useApi();
  const lang = useLang();
  return useQuery({
    queryKey: queryKeys.marketOrders(lang, marketId, status),
    queryFn: ({ signal }) => api.listMarketOrders(marketId, status, signal),
    refetchInterval: 30_000,
  });
}

export function useAdvanceOrder() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, status }: { orderId: string; status: OrderStatus }) => api.advanceOrderStatus(orderId, status),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['marketOrders'] }),
  });
}
