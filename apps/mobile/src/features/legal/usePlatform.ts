import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../shared/api';

/** The operator's legal links, the terms version to accept and the payment mode (GET /platform). */
export function usePlatform() {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.platform,
    queryFn: ({ signal }) => api.getPlatform(signal),
    staleTime: 5 * 60_000,
  });
}
