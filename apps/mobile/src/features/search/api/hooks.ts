import { useInfiniteQuery } from '@tanstack/react-query';

import { flattenPages, nextPage, queryKeys, useApi, type SearchProductsParams } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';

/** Product offers across all markets near the chosen place. */
export function useNearbyOffers(params: Omit<SearchProductsParams, 'page'>) {
  const api = useApi();
  const lang = useLang();
  const query = useInfiniteQuery({
    queryKey: queryKeys.offers(lang, params),
    queryFn: ({ pageParam, signal }) => api.searchProductsNearby({ ...params, page: pageParam }, signal),
    initialPageParam: 0,
    getNextPageParam: nextPage,
    enabled: params.q.trim().length >= 2,
  });
  return { ...query, offers: flattenPages(query.data) };
}
