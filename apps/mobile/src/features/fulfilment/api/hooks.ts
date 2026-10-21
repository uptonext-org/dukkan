import { useMutation, useQuery } from '@tanstack/react-query';

import { queryKeys, useApi, type FulfilmentType, type ReserveFulfilmentRequest } from '../../../shared/api';
import { useStoreCart } from '../../cart/api/hooks';

export function useSlots(marketId: string, type: FulfilmentType, addressId: string | undefined, enabled = true) {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.slots(marketId, type, addressId),
    queryFn: ({ signal }) => api.listSlots(marketId, type, addressId, signal),
    enabled: enabled && (type === 'PICKUP' || addressId !== undefined),
    staleTime: 15_000,
  });
}

export function useReserveFulfilment(marketId: string) {
  const api = useApi();
  const store = useStoreCart(marketId);
  return useMutation({
    mutationFn: (request: ReserveFulfilmentRequest) => api.reserveFulfilment(marketId, request),
    onSuccess: store,
  });
}
