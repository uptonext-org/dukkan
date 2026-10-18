import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';

export function usePromotions() {
  const api = useApi();
  return useQuery({ queryKey: queryKeys.promotions, queryFn: ({ signal }) => api.listPromotions(signal) });
}
