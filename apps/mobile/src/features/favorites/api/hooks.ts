import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';
import { useChosenPlace } from '../../../shared/location';
import { useAuth } from '../../auth/AuthProvider';
import { useAuthGate } from '../../auth/AuthGate';

export function useMarketFavorites(marketId: string) {
  const api = useApi();
  const lang = useLang();
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.marketFavorites(lang, marketId),
    queryFn: ({ signal }) => api.listMarketFavorites(marketId, signal),
    enabled: status === 'signedIn',
  });
}

export function useFavoriteMarkets() {
  const api = useApi();
  const lang = useLang();
  const { status } = useAuth();
  const { place } = useChosenPlace();
  return useQuery({
    queryKey: queryKeys.favoriteMarkets(lang),
    queryFn: ({ signal }) => api.listFavoriteMarkets(place?.location, signal),
    enabled: status === 'signedIn',
  });
}

/**
 * Sets `favorite` on every cached copy of the product (start page sections, lists, detail), so the
 * heart changes at once. Returns the same object where nothing changed (keeps renders cheap).
 */
export function withFavorite(value: unknown, productId: string, favorite: boolean): unknown {
  if (Array.isArray(value)) {
    const next = value.map((v) => withFavorite(v, productId, favorite));
    return next.some((v, i) => v !== value[i]) ? next : value;
  }
  if (value === null || typeof value !== 'object') {
    return value;
  }
  const record = value as Record<string, unknown>;
  let changed = false;
  const next: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(record)) {
    const mapped = withFavorite(child, productId, favorite);
    changed ||= mapped !== child;
    next[key] = mapped;
  }
  if (record.id === productId && 'favorite' in record && record.favorite !== favorite) {
    next.favorite = favorite;
    changed = true;
  }
  return changed ? next : value;
}

const PRODUCT_CACHES = ['product', 'products', 'marketHome'] as const;
const TOGGLE_PRODUCT_FAVORITE = ['toggleProductFavorite'] as const;

/**
 * Toggle a product heart; guests sign in first. The heart changes at once in every cached view;
 * on an error the views go back to what they showed before. Views are refreshed once the last of
 * several quick toggles has settled, so an intermediate server state does not flash.
 */
export function useToggleProductFavorite() {
  const api = useApi();
  const queryClient = useQueryClient();
  const { requireAuth } = useAuthGate();
  const mutation = useMutation({
    mutationKey: TOGGLE_PRODUCT_FAVORITE,
    mutationFn: ({ productId, favorite }: { productId: string; favorite: boolean }) =>
      favorite ? api.addFavoriteProduct(productId) : api.removeFavoriteProduct(productId),
    onMutate: async ({ productId, favorite }) => {
      // A refetch already on its way would overwrite the new heart with the old one.
      await Promise.all(PRODUCT_CACHES.map((key) => queryClient.cancelQueries({ queryKey: [key] })));
      const snapshots = PRODUCT_CACHES.flatMap((key) => queryClient.getQueriesData({ queryKey: [key] }));
      for (const key of PRODUCT_CACHES) {
        queryClient.setQueriesData({ queryKey: [key] }, (data: unknown) => withFavorite(data, productId, favorite));
      }
      return { snapshots };
    },
    onError: (_error, _variables, context) => {
      for (const [queryKey, data] of context?.snapshots ?? []) {
        queryClient.setQueryData(queryKey, data);
      }
    },
    onSettled: () => {
      if (queryClient.isMutating({ mutationKey: TOGGLE_PRODUCT_FAVORITE }) > 1) {
        return;
      }
      for (const key of ['marketFavorites', ...PRODUCT_CACHES]) {
        void queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
  const toggle = (productId: string, currentlyFavorite: boolean) =>
    requireAuth('favorites', () => mutation.mutate({ productId, favorite: !currentlyFavorite }));
  return { toggle, mutation };
}

export function useToggleMarketFavorite() {
  const api = useApi();
  const queryClient = useQueryClient();
  const { requireAuth } = useAuthGate();
  const mutation = useMutation({
    mutationFn: ({ marketId, favorite }: { marketId: string; favorite: boolean }) =>
      favorite ? api.addFavoriteMarket(marketId) : api.removeFavoriteMarket(marketId),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ['favoriteMarkets'] }),
  });
  const toggle = (marketId: string, currentlyFavorite: boolean) =>
    requireAuth('favorites', () => mutation.mutate({ marketId, favorite: !currentlyFavorite }));
  return { toggle, mutation };
}
