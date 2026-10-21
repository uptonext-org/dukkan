import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo } from 'react';

import { queryKeys, useApi, type Cart } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';
import { useAuth } from '../../auth/AuthProvider';
import { useAuthGate } from '../../auth/AuthGate';

/** All non-empty carts (one per market). Only for signed-in users. */
export function useCarts() {
  const api = useApi();
  const lang = useLang();
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.carts(lang),
    queryFn: ({ signal }) => api.listCarts(signal),
    enabled: status === 'signedIn',
  });
}

export function useCart(marketId: string) {
  const api = useApi();
  const lang = useLang();
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.cart(lang, marketId),
    queryFn: ({ signal }) => api.getCart(marketId, signal),
    enabled: status === 'signedIn' && marketId !== '',
  });
}

/** Writes a server cart into the cache and refreshes the cart list (tab badge, Warenkörbe). */
export function useStoreCart(marketId: string) {
  const queryClient = useQueryClient();
  const lang = useLang();
  return useCallback(
    (cart: Cart) => {
      queryClient.setQueryData(queryKeys.cart(lang, marketId), cart);
      void queryClient.invalidateQueries({ queryKey: ['carts'] });
    },
    [queryClient, lang, marketId],
  );
}

export function useSetCartQuantity(marketId: string) {
  const api = useApi();
  const store = useStoreCart(marketId);
  return useMutation({
    mutationFn: ({ productId, quantity }: { productId: string; quantity: number }) =>
      quantity <= 0 ? api.removeCartItem(marketId, productId) : api.setCartItemQuantity(marketId, productId, quantity),
    onSuccess: store,
  });
}

export function useClearCart(marketId: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.clearCart(marketId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cart'] });
      void queryClient.invalidateQueries({ queryKey: ['carts'] });
    },
  });
}

/**
 * Quantities in this market's cart plus a setter that asks guests to sign in first and then
 * continues (AuthGate). Errors surface through `error` of the returned mutation.
 */
export function useCartQuantities(marketId: string) {
  const { data: cart } = useCart(marketId);
  const mutation = useSetCartQuantity(marketId);
  const { requireAuth } = useAuthGate();
  const quantities = useMemo(() => {
    const map = new Map<string, number>();
    for (const item of cart?.items ?? []) {
      map.set(item.productId, item.quantity);
    }
    return map;
  }, [cart]);
  const { mutate } = mutation;
  const setQuantity = useCallback(
    (productId: string, quantity: number) => {
      requireAuth('cart', () => mutate({ productId, quantity }));
    },
    [requireAuth, mutate],
  );
  const busyProductId = mutation.isPending ? mutation.variables.productId : null;
  return { cart, quantities, setQuantity, busyProductId, error: mutation.error, reset: mutation.reset };
}
