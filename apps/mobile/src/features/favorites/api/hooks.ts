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

/** Toggle a product heart; guests sign in first. Refreshes every view that shows `favorite`. */
export function useToggleProductFavorite() {
  const api = useApi();
  const queryClient = useQueryClient();
  const { requireAuth } = useAuthGate();
  const mutation = useMutation({
    mutationFn: ({ productId, favorite }: { productId: string; favorite: boolean }) =>
      favorite ? api.addFavoriteProduct(productId) : api.removeFavoriteProduct(productId),
    onSettled: () => {
      for (const key of ['marketFavorites', 'product', 'products', 'marketHome']) {
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
