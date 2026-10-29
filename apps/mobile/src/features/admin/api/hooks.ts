import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';
import type { MarketStatus } from '../../../shared/api/types';
import { useLang } from '../../../shared/i18n';

/** Markets of a status for admins (all when null). */
export function useAdminMarkets(status: MarketStatus | null) {
  const api = useApi();
  const lang = useLang();
  return useQuery({ queryKey: queryKeys.adminMarkets(lang, status), queryFn: ({ signal }) => api.adminListMarkets(status, signal) });
}

/** Approve, suspend or return a market to review; refreshes the lists afterwards. */
export function useSetMarketStatus() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ marketId, status }: { marketId: string; status: MarketStatus }) => api.adminSetMarketStatus(marketId, status),
    onSettled: () => {
      // An approved or suspended market appears in or leaves the public lists.
      for (const key of ['adminMarkets', 'myMarkets', 'markets', 'market']) {
        void queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}
