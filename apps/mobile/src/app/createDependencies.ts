import { createFetchApiClient, createQueryClient } from '../shared/api';
import { env } from '../shared/config/env';
import { createKeychainTokenStorage, type TokenStorage } from '../shared/storage';
import { createAuthSession } from '../features/auth/session';
import type { AppDependencies } from './AppProviders';

export function createDependencies(
  tokenStorage: TokenStorage = createKeychainTokenStorage(),
): AppDependencies {
  const session = createAuthSession(tokenStorage);
  const apiClient = createFetchApiClient({
    baseUrl: env.apiBaseUrl,
    timeoutMs: env.requestTimeoutMs,
    getToken: () => session.getToken(),
    onUnauthorized: () => session.expire(),
  });
  return { apiClient, queryClient: createQueryClient(), session };
}
