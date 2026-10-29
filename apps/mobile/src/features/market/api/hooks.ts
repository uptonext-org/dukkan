import { useMutation, useQuery } from '@tanstack/react-query';

import { queryKeys, useApi, type FeedbackRequest } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';
import { useChosenPlace } from '../../../shared/location';

export function useMarket(marketId: string) {
  const api = useApi();
  const lang = useLang();
  const { place } = useChosenPlace();
  return useQuery({
    // Distance and "delivers here" depend on the chosen place.
    queryKey: [...queryKeys.market(lang, marketId), place?.location ?? null],
    queryFn: ({ signal }) => api.getMarket(marketId, place?.location, signal),
  });
}

/**
 * Whether customers can order from the market now. While it loads (or from an older server) the
 * answer is yes; the server refuses orders anyway when they are switched off.
 */
export function useAcceptsOrders(marketId: string): boolean {
  return useMarket(marketId).data?.acceptsOrders !== false;
}

export function useMarketHome(marketId: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({
    queryKey: queryKeys.marketHome(lang, marketId),
    queryFn: ({ signal }) => api.getMarketHome(marketId, signal),
  });
}

/** Whether the market takes feedback; off while loading or when it fails. */
export function useFeedbackAvailability(marketId: string) {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.feedbackAvailability(marketId),
    queryFn: ({ signal }) => api.getFeedbackAvailability(marketId, signal),
    staleTime: 5 * 60_000,
  });
}

export function useSendFeedback(marketId: string) {
  const api = useApi();
  return useMutation({ mutationFn: (request: FeedbackRequest) => api.sendFeedback(marketId, request) });
}
