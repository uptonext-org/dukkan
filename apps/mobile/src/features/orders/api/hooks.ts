import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi, type Order } from '../../../shared/api';

export function useOrders() {
  const api = useApi();
  return useQuery({ queryKey: queryKeys.orders, queryFn: ({ signal }) => api.listOrders(signal) });
}

export function useOrder(orderId: string) {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.order(orderId),
    queryFn: ({ signal }) => api.getOrder(orderId, signal),
  });
}

export function useCancelOrder() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderId: string) => api.cancelOrder(orderId),
    onSuccess: (order) => {
      queryClient.setQueryData<Order>(queryKeys.order(order.id), order);
      queryClient.setQueryData<Order[]>(queryKeys.orders, (old) =>
        old?.map((o) => (o.id === order.id ? order : o)),
      );
    },
  });
}
