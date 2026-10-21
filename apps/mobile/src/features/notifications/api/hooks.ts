import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi } from '../../../shared/api';
import { useAuth } from '../../auth/AuthProvider';

/** The inbox; texts are already in the user's language (written by the server). */
export function useNotifications() {
  const api = useApi();
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.notifications,
    queryFn: ({ signal }) => api.listNotifications(signal),
    enabled: status === 'signedIn',
    staleTime: 30_000,
  });
}

export function useMarkNotificationsRead() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.markNotificationsRead(),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  });
}
