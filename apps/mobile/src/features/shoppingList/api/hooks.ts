import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi, type Cart, type ShoppingListRequest } from '../../../shared/api';
import { useStoreCart } from '../../cart/api/hooks';
import type { CartUpdate } from '../model';

/** What customers may use, as switched by platform admins; all off while loading. */
export function useShoppingListSwitches(): { enabled: boolean; voice: boolean; media: boolean } {
  const data = useShoppingListCapabilities().data;
  // Older servers without switches: the list and voice on, photos and videos when the model is set up.
  const enabled = data !== undefined && data.enabled !== false;
  return {
    enabled,
    voice: enabled && (data.voice ?? true),
    media: enabled && (data.media ?? data.ai),
  };
}

export function useShoppingListCapabilities() {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.shoppingListCapabilities,
    queryFn: ({ signal }) => api.getShoppingListCapabilities(signal),
    staleTime: 5 * 60_000,
  });
}

export function useReadShoppingList(marketId: string) {
  const api = useApi();
  return useMutation({
    mutationFn: (request: ShoppingListRequest) => api.readShoppingList(marketId, request),
  });
}

/** Some lines reached the cart before one failed: those must not be added a second time. */
export class PartialApplyError extends Error {
  constructor(
    readonly reason: unknown,
    readonly applied: string[],
  ) {
    super('shopping list partly applied');
    this.name = 'PartialApplyError';
  }
}

/**
 * Sets the chosen quantities one after another; the last answer is the cart. A failure part-way
 * reports which products were already set (PartialApplyError).
 */
export function useApplyShoppingList(marketId: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  const store = useStoreCart(marketId);
  return useMutation({
    mutationFn: async (updates: CartUpdate[]) => {
      let cart: Cart | undefined;
      const applied: string[] = [];
      try {
        for (const update of updates) {
          cart = await api.setCartItemQuantity(marketId, update.productId, update.quantity);
          applied.push(update.productId);
        }
      } catch (error) {
        throw new PartialApplyError(error, applied);
      }
      return cart;
    },
    onSuccess: (cart) => {
      if (cart !== undefined) {
        store(cart);
      }
    },
    // Some lines may be in the cart already: show what the server has.
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['cart'] });
      void queryClient.invalidateQueries({ queryKey: ['carts'] });
    },
  });
}
