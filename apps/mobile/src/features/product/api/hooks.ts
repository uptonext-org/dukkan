import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';

export function useProduct(productId: string) {
  const api = useApi();
  return useQuery({
    queryKey: queryKeys.product(productId),
    queryFn: ({ signal }) => api.getProduct(productId, signal),
  });
}
