import { useQueryClient } from '@tanstack/react-query';
import { createContext, use, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { isApiError } from '../../shared/api';
import { AppText, Sheet } from '../../shared/ui';
import { useAuth } from './AuthProvider';
import { AuthForm } from './components/AuthForm';

export type AuthReason = 'cart' | 'favorites' | 'checkout' | 'browse' | 'account';

interface AuthGateValue {
  /** Runs `action` now when signed in; otherwise opens the sign-in sheet and runs it afterwards. */
  requireAuth: (reason: AuthReason, action?: () => void) => void;
}

const AuthGateContext = createContext<AuthGateValue | null>(null);

/**
 * Guest browsing (decision 14): everything is browsable; cart, favourites and checkout ask for
 * an account first and then continue. A 401 on a public endpoint means the backend switched on
 * `require-login-to-browse`, so the same sheet opens.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { status } = useAuth();
  const queryClient = useQueryClient();
  const [reason, setReason] = useState<AuthReason | null>(null);
  const pending = useRef<(() => void) | null>(null);
  const statusRef = useRef(status);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  const requireAuth = useCallback((why: AuthReason, action?: () => void) => {
    if (statusRef.current === 'signedIn') {
      action?.();
      return;
    }
    pending.current = action ?? null;
    setReason(why);
  }, []);

  useEffect(
    () =>
      queryClient.getQueryCache().subscribe((event) => {
        if (
          event.type === 'updated' &&
          event.action.type === 'error' &&
          isApiError(event.action.error) &&
          event.action.error.status === 401 &&
          statusRef.current === 'signedOut'
        ) {
          setReason((current) => current ?? 'browse');
        }
      }),
    [queryClient],
  );

  const value = useMemo(() => ({ requireAuth }), [requireAuth]);
  const close = () => {
    pending.current = null;
    setReason(null);
  };
  /** Signed in from the sheet: close it and continue with what the person was doing. */
  const continueAfterSignIn = () => {
    const action = pending.current;
    pending.current = null;
    setReason(null);
    action?.();
  };

  return (
    <AuthGateContext value={value}>
      {children}
      <Sheet visible={reason !== null} onClose={close} title={t('auth.sheetTitle')} testID="auth-sheet">
        {reason !== null && (
          <AppText variant="callout" color="textSecondary" style={{ marginBottom: 16 }}>
            {t(`auth.reason.${reason}`)}
          </AppText>
        )}
        <AuthForm onDone={continueAfterSignIn} />
      </Sheet>
    </AuthGateContext>
  );
}

export function useAuthGate(): AuthGateValue {
  const value = use(AuthGateContext);
  if (value === null) {
    throw new Error('useAuthGate must be used inside <AuthGate>.');
  }
  return value;
}
