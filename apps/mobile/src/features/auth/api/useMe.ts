import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';

export function useMe() {
  const api = useApi();
  return useQuery({ queryKey: queryKeys.me, queryFn: ({ signal }) => api.getCurrentUser(signal) });
}
