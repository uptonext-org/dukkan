import { useQueryClient } from '@tanstack/react-query';
import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ApiError, queryKeys, useApi, type AccountType, type AuthResponse, type LoginRequest, type RegisterRequest, type SocialProvider } from '../../shared/api';
import type { SocialSignIn } from '../../shared/auth';
import type { PushRegistration } from '../../shared/push';
import type { AuthSession } from './session';

export type AuthStatus = 'restoring' | 'signedOut' | 'signedIn';

interface AuthContextValue {
  status: AuthStatus;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  /** Providers the device offers (empty until Apple/Google sign-in is configured). */
  socialProviders: SocialProvider[];
  /** `false` when the user cancelled the native sheet. Throws ApiError on server errors. */
  socialSignIn: (
    provider: SocialProvider,
    accountType: AccountType,
    preferredLocale: string,
    acceptedTermsVersion?: string | null,
  ) => Promise<boolean>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({
  session,
  socialSignIn: social,
  pushRegistration,
  children,
}: {
  session: AuthSession;
  socialSignIn: SocialSignIn;
  pushRegistration: PushRegistration;
  children: ReactNode;
}) {
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
        // Session ended (logout or 401): drop user data, keep browsing data usable.
        queryClient.removeQueries({ queryKey: queryKeys.me });
        for (const key of ['notifications', 'carts', 'cart', 'addresses', 'orders', 'order', 'marketFavorites', 'favoriteMarkets', 'recentMarkets', 'myMarkets', 'listings', 'myPromotions', 'marketOrders']) {
          queryClient.removeQueries({ queryKey: [key] });
        }
        setStatus('signedOut');
      } else {
        setStatus('signedIn');
        // Public content now carries `favorite` flags etc.: refresh it.
        void queryClient.invalidateQueries();
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
      await session.start(response);
      // Best effort: push is optional, the inbox works without it.
      void pushRegistration
        .deviceToken()
        .then((device) => (device !== null ? api.registerDevice(device.token, device.platform) : undefined))
        .catch(() => undefined);
    },
    [api, pushRegistration, session, queryClient],
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

  const socialProviders = useMemo(() => social.providers(), [social]);
  const socialSignIn = useCallback(
    async (provider: SocialProvider, accountType: AccountType, preferredLocale: string, acceptedTermsVersion: string | null = null) => {
      const result = await social.signIn(provider);
      if (result.status === 'cancelled') {
        return false;
      }
      if (result.status === 'failed') {
        throw new ApiError({ kind: 'network', status: 0, code: 'SOCIAL_SIGN_IN_FAILED', detail: 'The provider sign-in failed.' });
      }
      await startSession(
        await api.socialSignIn({
          provider,
          idToken: result.idToken,
          nonce: result.nonce ?? null,
          firstName: result.firstName ?? null,
          lastName: result.lastName ?? null,
          accountType,
          preferredLocale,
          acceptedTermsVersion,
        }),
      );
      return true;
    },
    [api, social, startSession],
  );

  const logout = useCallback(async () => {
    const device = await pushRegistration.deviceToken().catch(() => null);
    if (device !== null) {
      await api.unregisterDevice(device.token).catch(() => undefined);
    }
    const refreshToken = session.getRefreshToken();
    if (refreshToken !== null) {
      // Best effort: the server ends the sign-in; offline, the local session ends anyway.
      await api.logout(refreshToken).catch(() => undefined);
    }
    await session.end();
  }, [api, pushRegistration, session]);

  const value = useMemo(
    () => ({ status, login, register, socialProviders, socialSignIn, logout }),
    [status, login, register, socialProviders, socialSignIn, logout],
  );
  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const value = use(AuthContext);
  if (value === null) {
    throw new Error('useAuth must be used inside <AuthProvider>.');
  }
  return value;
}
