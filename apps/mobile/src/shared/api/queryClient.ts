import { QueryClient } from '@tanstack/react-query';

import { isApiError } from './ApiError';

const MAX_RETRIES = 2;

/** Retry transient failures (network, timeout, 5xx) but never 4xx responses. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (isApiError(error) && error.isClientError) {
    return false;
  }
  return failureCount < MAX_RETRIES;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetry,
        staleTime: 30_000,
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
