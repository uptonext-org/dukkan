import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  flattenPages,
  nextPage,
  queryKeys,
  useApi,
  type CreateListingRequest,
  type MarketProfileRequest,
  type MerchantOrderSettings,
  type OrderSettings,
  type OrderStatus,
  type ProductDraftRequest,
  type TranslationRequest,
  type PromotionRequest,
  type SearchCatalogParams,
  type MediaPurpose,
  type UpdateListingRequest,
  type UploadFile,
} from '../../../shared/api';
import { LANGUAGES, useLang, type Language } from '../../../shared/i18n';

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

export function useOrderSettings(marketId: string) {
  const api = useApi();
  return useQuery({ queryKey: queryKeys.orderSettings(marketId), queryFn: ({ signal }) => api.getOrderSettings(marketId, signal) });
}

/** The stored settings without the read-only defaults, ready to send back with a change. */
export function settingsRequest(settings: MerchantOrderSettings, change: Partial<OrderSettings>): OrderSettings {
  return {
    ordersEnabled: settings.ordersEnabled,
    pausedUntil: settings.pausedUntil ?? null,
    closedFrom: settings.closedFrom ?? null,
    closedUntil: settings.closedUntil ?? null,
    deliveryCapacity: settings.deliveryCapacity ?? null,
    pickupCapacity: settings.pickupCapacity ?? null,
    deliveryLeadMinutes: settings.deliveryLeadMinutes ?? null,
    pickupLeadMinutes: settings.pickupLeadMinutes ?? null,
    daysAhead: settings.daysAhead ?? null,
    deliveryHours: settings.deliveryHours,
    autoAccept: settings.autoAccept,
    paymentMethods: settings.paymentMethods,
    defaultMaxOrderQuantity: settings.defaultMaxOrderQuantity ?? null,
    ...change,
  };
}

export function useUpdateOrderSettings(marketId: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: OrderSettings) => api.updateOrderSettings(marketId, request),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.orderSettings(marketId), updated);
      // What customers see of the market changes (order buttons, notice).
      void queryClient.invalidateQueries({ queryKey: ['markets'] });
      void queryClient.invalidateQueries({ queryKey: ['market'] });
      void queryClient.invalidateQueries({ queryKey: ['marketHome'] });
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

/** Uploads a photo for the market and resolves to the stored media (its `url` goes into a form field). */
export function useUploadMedia(marketId: string) {
  const api = useApi();
  return useMutation({
    mutationFn: ({ file, purpose }: { file: UploadFile; purpose: MediaPurpose }) => api.uploadMedia(marketId, file, purpose),
  });
}

/** AI product capture; the draft only fills the form, so nothing is invalidated. */
export function useProductDraft(marketId: string) {
  const api = useApi();
  return useMutation({ mutationFn: (request: ProductDraftRequest) => api.createProductDraft(marketId, request) });
}

export function useTranslateTexts() {
  const api = useApi();
  return useMutation({ mutationFn: (request: TranslationRequest) => api.translateTexts(request) });
}

/**
 * The languages a merchant fills in texts for: only the market's language unless platform admins
 * switched on MERCHANT_MULTILINGUAL (also while the switches load or fail).
 */
export function useMerchantLanguages(primary: Language): { languages: readonly (typeof LANGUAGES)[number][]; multilingual: boolean } {
  const api = useApi();
  const features = useQuery({ queryKey: queryKeys.features, queryFn: ({ signal }) => api.getFeatures(signal), staleTime: 5 * 60_000 });
  const multilingual = features.data?.MERCHANT_MULTILINGUAL === true;
  return { languages: multilingual ? LANGUAGES : LANGUAGES.filter((l) => l.code === primary), multilingual };
}

/** Customer feedback to my market, newest first, plus whether the market takes feedback. */
export function useMerchantFeedback(marketId: string) {
  const api = useApi();
  return useInfiniteQuery({
    queryKey: queryKeys.merchantFeedback(marketId),
    queryFn: ({ pageParam, signal }) => api.getMerchantFeedback(marketId, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => nextPage(last.feedback),
  });
}

export function useSetFeedbackEnabled(marketId: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => api.setFeedbackEnabled(marketId, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.merchantFeedback(marketId) }),
  });
}
