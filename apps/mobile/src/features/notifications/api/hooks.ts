import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys, useApi, type NotificationSettings } from '../../../shared/api';
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

export function useNotificationSettings() {
  const api = useApi();
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.notificationSettings,
    queryFn: ({ signal }) => api.getNotificationSettings(signal),
    enabled: status === 'signedIn',
  });
}

/** Optimistic: the switch moves at once and falls back if the server says no. */
export function useUpdateNotificationSettings() {
  const api = useApi();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: NotificationSettings) => api.updateNotificationSettings(settings),
    onMutate: async (settings) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.notificationSettings });
      const previous = queryClient.getQueryData<NotificationSettings>(queryKeys.notificationSettings);
      queryClient.setQueryData(queryKeys.notificationSettings, settings);
      return { previous };
    },
    onError: (_error, _settings, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(queryKeys.notificationSettings, context.previous);
      }
    },
    onSuccess: (saved) => queryClient.setQueryData(queryKeys.notificationSettings, saved),
  });
}
