import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { queryKeys, useApi, type ListProductsParams, type ProductPage } from '../../../shared/api';

export const PAGE_SIZE = 20;

export function useCategories() {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.categories,
    queryFn: ({ signal }) => api.listCategories(signal),
    staleTime: 5 * 60_000,
  });
}

export function nextPageParam(lastPage: ProductPage): number | undefined {
  return lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined;
}

/** Paginated product listing (browse, search, offers). */
export function useProductPages(params: Omit<ListProductsParams, 'page'>, enabled = true) {
  const api = useApi();
  const query = { size: PAGE_SIZE, ...params };
  return useInfiniteQuery({
    queryKey: queryKeys.products(query),
    queryFn: ({ pageParam, signal }) => api.listProducts({ ...query, page: pageParam }, signal),
    initialPageParam: 0,
    getNextPageParam: nextPageParam,
    select: (data) => data.pages.flatMap((page) => page.items),
    enabled,
  });
}
