import { NavigationContainer } from '@react-navigation/native';
import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';

import { AppProviders, type AppDependencies } from '../app/AppProviders';
import { createAuthSession } from '../features/auth/session';
import { RootNavigator } from '../navigation/RootNavigator';
import type { ApiClient } from '../shared/api';
import { createMemoryTokenStorage } from '../shared/storage';

export function createTestDependencies(apiClient: ApiClient, token: string | null = null): AppDependencies {
  return {
    apiClient,
    queryClient: new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
    }),
    session: createAuthSession(createMemoryTokenStorage(token)),
  };
}

/** Renders any element inside the app providers (no navigation). */
export function renderWithProviders(ui: ReactElement, dependencies: AppDependencies) {
  return render(<AppProviders dependencies={dependencies}>{ui}</AppProviders>);
}

/** Renders the real navigation tree, starting signed in when a token is given. */
export function renderApp(dependencies: AppDependencies) {
  return renderWithProviders(
    <NavigationContainer>
      <RootNavigator />
    </NavigationContainer>,
    dependencies,
  );
}
