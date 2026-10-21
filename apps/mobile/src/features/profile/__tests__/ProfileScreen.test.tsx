import { screen, userEvent, waitFor } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const base = { 'GET /market-types': [], 'GET /cuisines': [], 'GET /markets': page([market()]), 'GET /carts': [] };

async function openProfile() {
  const user = userEvent.setup();
  await user.press(await screen.findByRole('button', { name: 'Profil, Tab 4 von 4' }));
  return user;
}

describe('Profil', () => {
  it('registers a merchant account (with the UI language) and then shows "Mein Markt"', async () => {
    const merchant = { id: 'm1', email: 'mert@example.de', firstName: 'Mert', lastName: 'K', roles: ['MERCHANT'] };
    const fake = createFakeApiClient({
      ...base,
      'POST /auth/register': { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600, user: merchant },
      'GET /me': merchant,
      'GET /merchant/markets': [],
    });
    await renderApp(createTestDependencies(fake.client));
    const user = await openProfile();

    await user.press(await screen.findByRole('tab', { name: 'Registrieren' }));
    await user.press(screen.getByRole('tab', { name: 'Marktanbieter' }));
    await user.type(screen.getByLabelText('Vorname'), 'Mert');
    await user.type(screen.getByLabelText('Nachname'), 'K');
    await user.type(screen.getByLabelText('E-Mail'), 'mert@example.de');
    await user.type(screen.getByLabelText('Passwort'), 'short');
    await user.press(screen.getByTestId('auth-submit'));
    expect(await screen.findByText('Mindestens 10 Zeichen.', { exact: true })).toBeOnTheScreen();

    await user.clear(screen.getByLabelText('Passwort'));
    await user.type(screen.getByLabelText('Passwort'), 'long-enough-password');
    await user.press(screen.getByTestId('auth-submit'));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({ path: '/auth/register', body: expect.objectContaining({ accountType: 'MERCHANT', preferredLocale: 'de', email: 'mert@example.de' }) }),
      ),
    );
    expect(await screen.findByTestId('profile-my-market')).toBeOnTheScreen();
    await user.press(screen.getByTestId('profile-my-market'));
    expect(await screen.findByText('Noch kein Markt')).toBeOnTheScreen();
  });

  it('does not show "Mein Markt" to customers', async () => {
    const fake = createFakeApiClient({ ...base, 'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] } });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    await openProfile();
    expect(await screen.findByTestId('profile-orders')).toBeOnTheScreen();
    expect(screen.queryByTestId('profile-my-market')).not.toBeOnTheScreen();
  });
});
