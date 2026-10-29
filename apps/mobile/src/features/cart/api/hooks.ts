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

/**
 * Sets a cart line. `change` (+1, -1) is applied to the quantity in the cart when the request runs,
 * so quick taps add up; `quantity` sets it outright. Requests of one market run one after another
 * (scope), so an older answer never overwrites a newer cart.
 */
export function useSetCartQuantity(marketId: string) {
  const api = useApi();
  const lang = useLang();
  const queryClient = useQueryClient();
  const store = useStoreCart(marketId);
  return useMutation({
    scope: { id: `cart:${marketId}` },
    mutationFn: ({ productId, quantity, change }: { productId: string; quantity: number; change?: number }) => {
      const current = queryClient.getQueryData<Cart>(queryKeys.cart(lang, marketId))?.items.find((item) => item.productId === productId)?.quantity;
      const next = change !== undefined && current !== undefined ? Math.max(0, current + change) : quantity;
      return next <= 0 ? api.removeCartItem(marketId, productId) : api.setCartItemQuantity(marketId, productId, next);
    },
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
  const { status } = useAuth();
  const api = useApi();
  const lang = useLang();
  const queryClient = useQueryClient();
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
      if (status === 'signedIn') {
        mutate({ productId, quantity, change: quantity - (quantities.get(productId) ?? 0) });
        return;
      }
      // A guest's tap is a change (+1 / -1) to the cart they will have once signed in, which may
      // already hold the product: apply the change to that cart instead of overwriting it.
      const change = quantity - (quantities.get(productId) ?? 0);
      requireAuth('cart', () => {
        void queryClient
          .query({ queryKey: queryKeys.cart(lang, marketId), queryFn: ({ signal }) => api.getCart(marketId, signal) })
          .then((server) => {
            const current = server.items.find((item) => item.productId === productId)?.quantity ?? 0;
            mutate({ productId, quantity: Math.max(0, current + change) });
          })
          .catch(() => mutate({ productId, quantity }));
      });
    },
    [status, quantities, requireAuth, queryClient, lang, marketId, api, mutate],
  );
  const busyProductId = mutation.isPending ? mutation.variables.productId : null;
  return { cart, quantities, setQuantity, busyProductId, error: mutation.error, reset: mutation.reset };
}
