import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ApiProvider, type ApiClient } from '../shared/api';
import { AuthProvider } from '../features/auth/AuthProvider';
import type { AuthSession } from '../features/auth/session';

export interface AppDependencies {
  apiClient: ApiClient;
  queryClient: QueryClient;
  session: AuthSession;
}

/** All app-wide providers. Tests pass in-memory dependencies (fake ApiClient, memory token storage). */
export function AppProviders({
  dependencies,
  children,
}: {
  dependencies: AppDependencies;
  children: ReactNode;
}) {
  return (
    <SafeAreaProvider>
      <ApiProvider client={dependencies.apiClient}>
        <QueryClientProvider client={dependencies.queryClient}>
          <AuthProvider session={dependencies.session}>{children}</AuthProvider>
        </QueryClientProvider>
      </ApiProvider>
    </SafeAreaProvider>
  );
}
