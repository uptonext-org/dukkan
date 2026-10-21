import { useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';
import { useChosenPlace } from '../../../shared/location';

export function useMarket(marketId: string) {
  const api = useApi();
  const lang = useLang();
  const { place } = useChosenPlace();
  return useQuery({
    queryKey: queryKeys.market(lang, marketId),
    queryFn: ({ signal }) => api.getMarket(marketId, place?.location, signal),
  });
}

export function useMarketHome(marketId: string) {
  const api = useApi();
  const lang = useLang();
  return useQuery({
    queryKey: queryKeys.marketHome(lang, marketId),
    queryFn: ({ signal }) => api.getMarketHome(marketId, signal),
  });
}
