import { createContext, use, useMemo, type ReactNode } from 'react';

import type { ApiClient } from './client';
import { createDukkanApi, type DukkanApi } from './dukkanApi';

const ApiContext = createContext<DukkanApi | null>(null);

export function ApiProvider({ client, children }: { client: ApiClient; children: ReactNode }) {
  const api = useMemo(() => createDukkanApi(client), [client]);
  return <ApiContext value={api}>{children}</ApiContext>;
}

export function useApi(): DukkanApi {
  const api = use(ApiContext);
  if (api === null) {
    throw new Error('useApi must be used inside <ApiProvider>.');
  }
  return api;
}
