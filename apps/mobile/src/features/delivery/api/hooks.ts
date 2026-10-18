import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';

export function useDeliverySlots(addressId: string) {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.deliverySlots(addressId),
    queryFn: ({ signal }) => api.listDeliverySlots(addressId, signal),
    staleTime: 15_000,
  });
}
