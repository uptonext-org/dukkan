import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { flattenPages, nextPage, queryKeys, useApi, type ListMarketProductsParams } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';

export function useCategories(marketId: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({
    queryKey: queryKeys.categories(lang, marketId),
    queryFn: ({ signal }) => api.listMarketCategories(marketId, signal),
    staleTime: 5 * 60_000,
  });
}

export function useMarketProducts(marketId: string, params: Omit<ListMarketProductsParams, 'page'>, enabled = true) {
  const api = useApi();
  const lang = useLang();
  const query = useInfiniteQuery({
    queryKey: queryKeys.products(lang, marketId, params),
    queryFn: ({ pageParam, signal }) => api.listMarketProducts(marketId, { ...params, page: pageParam }, signal),
    initialPageParam: 0,
    getNextPageParam: nextPage,
    enabled,
  });
  return { ...query, products: flattenPages(query.data) };
}

export function useProduct(productId: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({
    queryKey: queryKeys.product(lang, productId),
    queryFn: ({ signal }) => api.getProduct(productId, signal),
  });
}

export function useSuggestions(marketId: string, q: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({
    queryKey: queryKeys.suggestions(lang, marketId, q),
    queryFn: ({ signal }) => api.marketSearchSuggestions(marketId, q, signal),
    enabled: q.trim().length >= 2,
    staleTime: 60_000,
  });
}
