import type { SocialProvider } from '../api';

/** What the native Apple/Google sheet returned. Names come from Apple on the first sign-in only. */
export type SocialSignInResult =
  | { status: 'signedIn'; idToken: string; nonce?: string; firstName?: string; lastName?: string }
  | { status: 'cancelled' }
  | { status: 'failed' };

/**
 * Native "Sign in with Apple" / "Sign in with Google" behind an interface (like PhotoPicker), so
 * screens and tests never touch native modules. The app ships `createUnavailableSocialSignIn()`
 * until the OAuth clients exist; docs/setup.md shows the adapter for
 * `@invertase/react-native-apple-authentication` and `@react-native-google-signin/google-signin`.
 */
export interface SocialSignIn {
  /** Providers to show buttons for (Apple first, as Apple's guidelines ask). */
  providers(): SocialProvider[];
  signIn(provider: SocialProvider): Promise<SocialSignInResult>;
}

export function createUnavailableSocialSignIn(): SocialSignIn {
  return {
    providers: () => [],
    signIn: () => Promise.resolve({ status: 'failed' }),
  };
}

/** Test double: offers `providers` and answers every sign-in with the queued results in order. */
export function createFakeSocialSignIn(providers: SocialProvider[], ...results: SocialSignInResult[]): SocialSignIn & { calls: SocialProvider[] } {
  const calls: SocialProvider[] = [];
  return {
    calls,
    providers: () => providers,
    signIn: (provider) => {
      calls.push(provider);
      return Promise.resolve(results.shift() ?? { status: 'cancelled' });
    },
  };
}
