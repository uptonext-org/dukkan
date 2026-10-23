import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, userEvent, waitFor } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import { ApiError, type ApiRequest } from '../../../shared/api';
import { createFakeSocialSignIn } from '../../../shared/auth';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp, renderInAppRoot } from '../../../test/render';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { ResetPasswordScreen } from '../screens/ResetPasswordScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();
const base = { 'GET /market-types': [], 'GET /cuisines': [], 'GET /markets': page([market()]), 'GET /carts': [] };
const ada = { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER' as const] };
const signedIn = { accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-1', refreshExpiresIn: 2_592_000, user: ada };

async function openProfile() {
  const user = userEvent.setup();
  await user.press(await screen.findByRole('button', { name: 'Profil, Tab 4 von 4' }));
  return user;
}

describe('Anmeldung: Passwort vergessen, Apple/Google, Abmelden (B-39)', () => {
  it('requests a reset link without revealing whether the account exists', async () => {
    const fake = createFakeApiClient({ ...base, 'POST /auth/password-reset': () => undefined });
    await renderApp(createTestDependencies(fake.client));
    const user = await openProfile();

    await user.type(await screen.findByLabelText('E-Mail'), 'ada@example.de');
    await user.press(screen.getByTestId('auth-forgot'));
    expect(screen.getByTestId('forgot-email')).toHaveDisplayValue('ada@example.de');
    await user.press(screen.getByTestId('forgot-submit'));

    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'POST', path: '/auth/password-reset', body: { email: 'ada@example.de' }, anonymous: true })));
    expect(await screen.findByTestId('forgot-sent')).toHaveTextContent('Wenn es ein Konto mit dieser Adresse gibt, ist der Link unterwegs.', { exact: false });
    await user.press(screen.getByTestId('forgot-back'));
    expect(await screen.findByTestId('auth-submit')).toBeOnTheScreen();
  });

  it('offers only configured providers, ignores a cancelled sheet and signs in with the ID token', async () => {
    const social = createFakeSocialSignIn(['APPLE', 'GOOGLE'], { status: 'cancelled' }, { status: 'signedIn', idToken: 'id-token', nonce: 'n-1', firstName: 'Ada', lastName: 'L' });
    const fake = createFakeApiClient({ ...base, 'POST /auth/social': signedIn, 'GET /me': ada, 'POST /auth/logout': () => undefined });
    await renderApp(createTestDependencies(fake.client, { socialSignIn: social }));
    const user = await openProfile();

    expect(await screen.findByRole('button', { name: 'Weiter mit Apple' })).toBeOnTheScreen();
    await user.press(screen.getByTestId('auth-social-GOOGLE'));
    expect(fake.calls.some((c) => c.path === '/auth/social')).toBe(false);
    await user.press(screen.getByTestId('auth-social-APPLE'));

    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({
          path: '/auth/social',
          body: { provider: 'APPLE', idToken: 'id-token', nonce: 'n-1', firstName: 'Ada', lastName: 'L', accountType: 'CUSTOMER', preferredLocale: 'de', acceptedTermsVersion: null },
        }),
      ),
    );
    expect(social.calls).toEqual(['GOOGLE', 'APPLE']);
    expect(await screen.findByTestId('profile-orders')).toBeOnTheScreen();

    // Logout ends the sign-in on the server too.
    await user.press(screen.getByRole('button', { name: 'Abmelden' }));
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ path: '/auth/logout', body: { refreshToken: 'refresh-1' } })));
    expect(await screen.findByTestId('auth-submit')).toBeOnTheScreen();
  });

  it('shows no provider buttons while sign-in with Apple/Google is not set up', async () => {
    await renderApp(createTestDependencies(createFakeApiClient(base).client));
    await openProfile();
    expect(await screen.findByTestId('auth-submit')).toBeOnTheScreen();
    expect(screen.queryByTestId('auth-social-APPLE')).not.toBeOnTheScreen();
  });
});

describe('Neues Passwort und Passwort ändern', () => {
  function renderScreen(name: 'ResetPassword' | 'ChangePassword', fake: ReturnType<typeof createFakeApiClient>) {
    return renderInAppRoot(
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {name === 'ResetPassword' ? (
          <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} initialParams={{ token: 'reset-token' }} />
        ) : (
          <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} />
        )}
      </Stack.Navigator>,
      createTestDependencies(fake.client, { token: 'jwt' }),
    );
  }

  it('sets a new password with the token from the link and explains a used link', async () => {
    let attempts = 0;
    const fake = createFakeApiClient({
      'POST /auth/password-reset/confirm': () => {
        attempts += 1;
        if (attempts === 1) {
          throw new ApiError({ kind: 'http', status: 400, code: 'VALIDATION_FAILED', detail: 'x', fieldCodes: { token: 'INVALID_RESET_TOKEN' }, fieldErrors: { token: 'used' } });
        }
        return undefined;
      },
    });
    await renderScreen('ResetPassword', fake);
    const user = userEvent.setup();

    await user.type(await screen.findByTestId('reset-password'), 'kurz');
    await user.press(screen.getByTestId('reset-submit'));
    expect(await screen.findByText('Mindestens 10 Zeichen.')).toBeOnTheScreen();
    await user.clear(screen.getByTestId('reset-password'));
    await user.type(screen.getByTestId('reset-password'), 'ganz-neues-passwort');
    await user.press(screen.getByTestId('reset-submit'));
    expect(await screen.findByTestId('reset-error')).toHaveTextContent('Der Link ist abgelaufen oder wurde schon benutzt.', { exact: false });
    await user.press(screen.getByTestId('reset-submit'));
    expect(await screen.findByTestId('reset-done')).toBeOnTheScreen();
    expect((fake.calls.at(-1) as ApiRequest).body).toEqual({ token: 'reset-token', newPassword: 'ganz-neues-passwort' });
  });

  it('changes the password after checking the current one', async () => {
    let attempts = 0;
    const fake = createFakeApiClient({
      'PUT /me/password': () => {
        attempts += 1;
        if (attempts === 1) {
          throw new ApiError({ kind: 'http', status: 400, code: 'VALIDATION_FAILED', detail: 'x', fieldCodes: { currentPassword: 'Incorrect' }, fieldErrors: { currentPassword: 'wrong' } });
        }
        return undefined;
      },
    });
    await renderScreen('ChangePassword', fake);
    const user = userEvent.setup();

    await user.type(await screen.findByTestId('change-current'), 'falsch');
    await user.type(screen.getByTestId('change-next'), 'neues-passwort-123');
    await user.press(screen.getByTestId('change-submit'));
    expect(await screen.findByText('Das aktuelle Passwort stimmt nicht.')).toBeOnTheScreen();
    await user.press(screen.getByTestId('change-submit'));
    expect(await screen.findByTestId('change-done')).toBeOnTheScreen();
    expect(fake.calls.at(-1)?.body).toEqual({ currentPassword: 'falsch', newPassword: 'neues-passwort-123' });
  });
});
