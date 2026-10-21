import { createContext, useContext, type ReactNode } from 'react';

import type { Api, Session } from './client';

interface ApiContextValue {
  api: Api;
  session: Session | null;
}

const ApiContext = createContext<ApiContextValue | null>(null);

export function ApiProvider({ value, children }: { value: ApiContextValue; children: ReactNode }) {
  return <ApiContext value={value}>{children}</ApiContext>;
}

function useApiContext(): ApiContextValue {
  const value = useContext(ApiContext);
  if (value === null) {
    throw new Error('ApiProvider missing');
  }
  return value;
}

export function useApi(): Api {
  return useApiContext().api;
}

export function useSession(): Session | null {
  return useApiContext().session;
}
