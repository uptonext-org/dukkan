import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi, type Address, type AddressRequest } from '../../../shared/api';
import { POSTAL_CODE_PATTERN } from '../validation';

export function useAddresses() {
  const api = useApi();
  return useQuery({ queryKey: queryKeys.addresses, queryFn: ({ signal }) => api.listAddresses(signal) });
}

export function useSaveAddress(addressId: string | undefined) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AddressRequest) =>
      addressId === undefined ? api.createAddress(request) : api.updateAddress(addressId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  });
}

export function useDeleteAddress() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (addressId: string) => api.deleteAddress(addressId),
    onSuccess: (_result, addressId) => {
      queryClient.setQueryData<Address[]>(queryKeys.addresses, (old) =>
        old?.filter((a) => a.id !== addressId),
      );
      void queryClient.invalidateQueries({ queryKey: queryKeys.addresses });
      void queryClient.invalidateQueries({ queryKey: queryKeys.cart });
    },
  });
}

/** Serviceability pre-check; only runs for a syntactically valid postal code. */
export function useDeliveryAreaCheck(postalCode: string | null) {
  const api = useApi();
  const code = postalCode ?? '';
  return useQuery({
    queryKey: queryKeys.deliveryArea(code),
    queryFn: ({ signal }) => api.checkDeliveryArea(code, signal),
    enabled: POSTAL_CODE_PATTERN.test(code),
    staleTime: 10 * 60_000,
  });
}
