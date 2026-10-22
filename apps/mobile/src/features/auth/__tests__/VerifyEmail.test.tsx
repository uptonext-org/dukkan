import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, userEvent, waitFor } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import { ApiError, type ApiRequest } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp, renderInAppRoot } from '../../../test/render';
import { VerifyEmailScreen } from '../screens/VerifyEmailScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

function openLink(token: string, handler: (request: ApiRequest) => unknown) {
  const fake = createFakeApiClient({ 'POST /auth/verify-email': handler });
  return {
    fake,
    render: () =>
      renderInAppRoot(
        <Stack.Navigator screenOptions={{ headerShown: false }}>
          <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} initialParams={{ token }} />
        </Stack.Navigator>,
        createTestDependencies(fake.client),
      ),
  };
}

describe('E-Mail bestätigen (B-39b)', () => {
  it('confirms the address from the welcome-mail link', async () => {
    const { fake, render } = openLink('tok-123', () => undefined);
    await render();
    expect(await screen.findByTestId('verify-done')).toHaveTextContent('Danke, deine E-Mail-Adresse ist bestätigt.');
    expect(fake.calls).toEqual([expect.objectContaining({ method: 'POST', path: '/auth/verify-email', body: { token: 'tok-123' }, anonymous: true })]);
  });

  it('explains a used or expired link', async () => {
    const { render } = openLink('old', () => {
      throw new ApiError({ kind: 'http', status: 400, code: 'VALIDATION_FAILED', fieldCodes: { token: 'INVALID_VERIFICATION_TOKEN' } });
    });
    await render();
    expect(await screen.findByTestId('verify-error')).toHaveTextContent('Der Bestätigungslink ist ungültig', { exact: false });
  });

  it('reminds unconfirmed users in the profile and resends the mail', async () => {
    const ada = { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER' as const], emailVerified: false };
    const fake = createFakeApiClient({
      'GET /market-types': [],
      'GET /cuisines': [],
      'GET /markets': page([market()]),
      'GET /carts': [],
      'GET /me': ada,
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'POST /me/email-verification': () => undefined,
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = userEvent.setup();
    await user.press(await screen.findByRole('button', { name: 'Profil, Tab 4 von 4' }));
    expect(await screen.findByTestId('verify-banner')).toBeOnTheScreen();
    await user.press(screen.getByTestId('verify-resend'));
    expect(await screen.findByText('Gesendet. Schau auch im Spam-Ordner nach.')).toBeOnTheScreen();
    await waitFor(() => expect(fake.calls.some((c) => c.method === 'POST' && c.path === '/me/email-verification')).toBe(true));
  });
});
