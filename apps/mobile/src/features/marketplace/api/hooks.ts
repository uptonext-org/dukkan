import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { flattenPages, nextPage, queryKeys, useApi, type ListMarketsParams } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';

const DAY = 24 * 60 * 60 * 1000;

export function useMarketTypes() {
  const api = useApi();
  const lang = useLang();
  return useQuery({ queryKey: queryKeys.marketTypes(lang), queryFn: ({ signal }) => api.listMarketTypes(signal), staleTime: DAY });
}

export function useCuisines() {
  const api = useApi();
  const lang = useLang();
  return useQuery({ queryKey: queryKeys.cuisines(lang), queryFn: ({ signal }) => api.listCuisines(signal), staleTime: DAY });
}

export function useMarkets(params: Omit<ListMarketsParams, 'page'>) {
  const api = useApi();
  const lang = useLang();
  const query = useInfiniteQuery({
    queryKey: queryKeys.markets(lang, params),
    queryFn: ({ pageParam, signal }) => api.listMarkets({ ...params, page: pageParam }, signal),
    initialPageParam: 0,
    getNextPageParam: nextPage,
  });
  return { ...query, markets: flattenPages(query.data) };
}

/** "Meine Märkte": ordered-from markets (signed in) and the markets visited on this device. */
export function useRecentMarkets(visited: string[], at: { lat: number; lng: number } | undefined, signedIn: boolean, enabled: boolean) {
  const api = useApi();
  const lang = useLang();
  // The app orders the result by its own visit times, so a new visit order needs no new request.
  const ids = [...visited].sort();
  return useQuery({
    queryKey: [...queryKeys.recentMarkets(lang, ids, signedIn), at ?? null],
    queryFn: ({ signal }) => api.listRecentMarkets(ids, at, signal),
    enabled,
    placeholderData: keepPreviousData,
  });
}
