import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';
import { useAuth } from '../AuthProvider';

export function useMe() {
  const api = useApi();
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: ({ signal }) => api.getCurrentUser(signal),
    enabled: status === 'signedIn',
  });
}

export function useIsAdmin(): boolean {
  const { data } = useMe();
  return data?.roles.includes('ADMIN') === true;
}

export function useIsMerchant(): boolean {
  const { data } = useMe();
  return data?.roles.includes('MERCHANT') === true;
}
