import { ApiError, type ApiClient, type ApiRequest } from '../shared/api';

export type FakeHandler = (request: ApiRequest) => unknown;

/**
 * In-memory ApiClient for tests. Handlers are keyed by `"METHOD /path"`; a handler may return
 * a value or throw (e.g. an ApiError). Unknown routes fail loudly with 404.
 */
export function createFakeApiClient(handlers: Record<string, FakeHandler | object>) {
  const calls: ApiRequest[] = [];
  const client: ApiClient = {
    request<T>(request: ApiRequest): Promise<T> {
      calls.push(request);
      const handler = handlers[`${request.method} ${request.path}`];
      if (handler === undefined) {
        return Promise.reject(
          new ApiError({ kind: 'http', status: 404, code: 'NOT_FOUND', detail: `No fake for ${request.method} ${request.path}` }),
        );
      }
      try {
        const result = typeof handler === 'function' ? (handler as FakeHandler)(request) : handler;
        return Promise.resolve(result as T);
      } catch (error) {
        return Promise.reject(error instanceof Error ? error : new Error(String(error)));
      }
    },
  };
  return { client, calls };
}
