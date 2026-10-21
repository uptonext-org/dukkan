import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { i18n as I18n } from 'i18next';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { AuthGate } from '../features/auth/AuthGate';
import { AuthProvider } from '../features/auth/AuthProvider';
import type { AuthSession } from '../features/auth/session';
import { ApiProvider, type ApiClient } from '../shared/api';
import { LanguageProvider, type LanguageStore } from '../shared/i18n';
import { LocationProvider, type LocationService } from '../shared/location';
import type { KeyValueStorage } from '../shared/storage';
import { ThemeProvider } from '../shared/theme';

export interface AppDependencies {
  apiClient: ApiClient;
  queryClient: QueryClient;
  session: AuthSession;
  languageStore: LanguageStore;
  i18n: I18n;
  storage: KeyValueStorage;
  locationService: LocationService;
}

/** All app-wide providers. Tests pass in-memory dependencies (fake ApiClient, memory storage). */
export function AppProviders({
  dependencies,
  children,
  scheme,
}: {
  dependencies: AppDependencies;
  children: ReactNode;
  scheme?: 'light' | 'dark';
}) {
  return (
    <SafeAreaProvider>
      <LanguageProvider store={dependencies.languageStore} i18n={dependencies.i18n}>
        <ThemeProvider {...(scheme !== undefined ? { scheme } : {})}>
          <ApiProvider client={dependencies.apiClient}>
            <QueryClientProvider client={dependencies.queryClient}>
              <AuthProvider session={dependencies.session}>
                <LocationProvider storage={dependencies.storage} service={dependencies.locationService}>
                  <AuthGate>{children}</AuthGate>
                </LocationProvider>
              </AuthProvider>
            </QueryClientProvider>
          </ApiProvider>
        </ThemeProvider>
      </LanguageProvider>
    </SafeAreaProvider>
  );
}
