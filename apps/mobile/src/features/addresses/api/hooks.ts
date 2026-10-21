import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi, type AddressRequest } from '../../../shared/api';
import { useAuth } from '../../auth/AuthProvider';

export function useAddresses() {
  const api = useApi();
  const { status } = useAuth();
  return useQuery({ queryKey: queryKeys.addresses, queryFn: ({ signal }) => api.listAddresses(signal), enabled: status === 'signedIn' });
}

export function useSaveAddress(addressId?: string) {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: AddressRequest) =>
      addressId !== undefined ? api.updateAddress(addressId, request) : api.createAddress(request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  });
}

export function useDeleteAddress() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (addressId: string) => api.deleteAddress(addressId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  });
}
