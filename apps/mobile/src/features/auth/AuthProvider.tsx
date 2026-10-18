import { useQueryClient } from '@tanstack/react-query';
import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import {
  queryKeys,
  useApi,
  type AuthResponse,
  type LoginRequest,
  type RegisterRequest,
} from '../../shared/api';
import type { AuthSession } from './session';

export type AuthStatus = 'restoring' | 'signedOut' | 'signedIn';

interface AuthContextValue {
  status: AuthStatus;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ session, children }: { session: AuthSession; children: ReactNode }) {
  const api = useApi();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('restoring');

  useEffect(() => {
    let active = true;
    session
      .restore()
      .then((token) => {
        if (active) {
          setStatus(token === null ? 'signedOut' : 'signedIn');
        }
      })
      .catch(() => {
        if (active) {
          setStatus('signedOut');
        }
      });
    const unsubscribe = session.subscribe((token) => {
      if (token === null) {
        // Session ended (logout or 401): drop all user data from the cache.
        queryClient.clear();
        setStatus('signedOut');
      } else {
        setStatus('signedIn');
      }
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, [session, queryClient]);

  const startSession = useCallback(
    async (response: AuthResponse) => {
      queryClient.setQueryData(queryKeys.me, response.user);
      await session.start(response.accessToken);
    },
    [session, queryClient],
  );

  const login = useCallback(
    async (credentials: LoginRequest) => {
      await startSession(await api.login(credentials));
    },
    [api, startSession],
  );

  const register = useCallback(
    async (request: RegisterRequest) => {
      await startSession(await api.register(request));
    },
    [api, startSession],
  );

  const logout = useCallback(() => session.end(), [session]);

  const value = useMemo(() => ({ status, login, register, logout }), [status, login, register, logout]);
  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const value = use(AuthContext);
  if (value === null) {
    throw new Error('useAuth must be used inside <AuthProvider>.');
  }
  return value;
}
