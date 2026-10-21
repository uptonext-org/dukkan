import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';

import { queryKeys, useApi } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';
import { useAuth } from '../../auth/AuthProvider';

export function useOrders() {
  const api = useApi();
  const lang = useLang();
  const { status } = useAuth();
  return useQuery({ queryKey: queryKeys.orders(lang), queryFn: ({ signal }) => api.listOrders(signal), enabled: status === 'signedIn' });
}

export function useOrder(orderId: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({ queryKey: queryKeys.order(lang, orderId), queryFn: ({ signal }) => api.getOrder(orderId, signal) });
}

function newIdempotencyKey(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/** Simulated checkout. One Idempotency-Key per attempt; a retry after a failure reuses it. */
export function usePlaceOrder(marketId: string) {
  const api = useApi();
  const lang = useLang();
  const queryClient = useQueryClient();
  const key = useRef<string | null>(null);
  return useMutation({
    mutationFn: () => {
      key.current ??= newIdempotencyKey();
      return api.placeOrder(marketId, key.current);
    },
    onSuccess: (order) => {
      key.current = null;
      queryClient.setQueryData(queryKeys.order(lang, order.id), order);
      for (const prefix of ['cart', 'carts', 'orders', 'slots']) {
        void queryClient.invalidateQueries({ queryKey: [prefix] });
      }
    },
  });
}

export function useCancelOrder() {
  const api = useApi();
  const lang = useLang();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderId: string) => api.cancelOrder(orderId),
    onSuccess: (order) => {
      queryClient.setQueryData(queryKeys.order(lang, order.id), order);
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
  });
}
