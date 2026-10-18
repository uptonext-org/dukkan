import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';

export const MIN_SUGGESTION_LENGTH = 2;

export function useSuggestions(query: string, enabled: boolean) {
  const api = useApi();
  const q = query.trim();
  return useQuery({
    queryKey: queryKeys.suggestions(q),
    queryFn: ({ signal }) => api.searchSuggestions(q, 5, signal),
    enabled: enabled && q.length >= MIN_SUGGESTION_LENGTH && q.length <= 100,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}
