import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo, useRef } from 'react';

import { queryKeys, useApi, type Cart, type Order, type ReserveSlotRequest } from '../../../shared/api';

export function useCart() {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.cart,
    queryFn: ({ signal }) => api.getCart(signal),
    staleTime: 10_000,
  });
}

/** Sets a line quantity (0 removes). The server's returned Cart replaces the cache. */
export function useSetCartQuantity() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, quantity }: { productId: string; quantity: number }) =>
      quantity === 0 ? api.removeCartItem(productId) : api.setCartItemQuantity(productId, quantity),
    onSuccess: (cart) => queryClient.setQueryData<Cart>(queryKeys.cart, cart),
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart }),
  });
}

/** productId -> quantity lookup for showing cart state on product tiles. */
export function useCartQuantities(): ReadonlyMap<string, number> {
  const { data } = useCart();
  return useMemo(() => new Map((data?.items ?? []).map((item) => [item.productId, item.quantity])), [data]);
}

/** Convenience for lists: stable change handler plus the product currently being updated. */
export function useCartUpdater() {
  const mutation = useSetCartQuantity();
  const { mutate } = mutation;
  const onChangeQuantity = useCallback(
    (productId: string, quantity: number) => mutate({ productId, quantity }),
    [mutate],
  );
  const pendingProductId = mutation.isPending ? mutation.variables.productId : undefined;
  return { onChangeQuantity, pendingProductId, error: mutation.error };
}

export function useReserveSlot() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ReserveSlotRequest) => api.reserveDeliverySlot(request),
    onSuccess: (cart, request) => {
      queryClient.setQueryData<Cart>(queryKeys.cart, cart);
      void queryClient.invalidateQueries({ queryKey: queryKeys.deliverySlots(request.addressId) });
    },
  });
}

function newIdempotencyKey(): string {
  const random = () => Math.random().toString(16).slice(2, 10);
  return `${Date.now().toString(16)}-${random()}-${random()}`;
}

/**
 * Simulated checkout. One idempotency key is reused across retries of the same attempt
 * and rotated after a successful order.
 */
export function usePlaceOrder() {
  const api = useApi();
  const queryClient = useQueryClient();
  const keyRef = useRef<string | null>(null);
  return useMutation({
    mutationFn: () => {
      keyRef.current ??= newIdempotencyKey();
      return api.placeOrder(keyRef.current);
    },
    onSuccess: (order: Order) => {
      keyRef.current = null;
      queryClient.setQueryData(queryKeys.order(order.id), order);
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orders });
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}
