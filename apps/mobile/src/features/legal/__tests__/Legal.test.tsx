import { screen, userEvent, waitFor } from '@testing-library/react-native';
import { Linking } from 'react-native';

import type { Platform } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const base = { 'GET /market-types': [], 'GET /cuisines': [], 'GET /markets': page([market()]), 'GET /carts': [] };
const ada = { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER' as const], emailVerified: true };
const signedIn = { accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'refresh-1', refreshExpiresIn: 2_592_000, user: ada };
const PLATFORM: Platform = {
  legal: {
    imprintUrl: 'https://dukkan.example/impressum',
    privacyUrl: 'https://dukkan.example/datenschutz',
    termsUrl: 'https://dukkan.example/agb',
    termsVersion: '2026-11',
  },
  supportEmail: 'hilfe@dukkan.example',
  payment: 'ON_SITE',
};

async function openProfile() {
  const user = userEvent.setup();
  await user.press(await screen.findByRole('button', { name: 'Profil, Tab 4 von 4' }));
  return user;
}

describe('Rechtliches: Impressum, Datenschutz, AGB', () => {
  it('links the operator pages from the profile', async () => {
    const open = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined);
    await renderApp(createTestDependencies(createFakeApiClient({ ...base, 'GET /platform': PLATFORM }).client));
    const user = await openProfile();

    await user.press(await screen.findByRole('button', { name: 'Impressum' }));
    await user.press(screen.getByRole('button', { name: 'Datenschutz' }));
    await user.press(screen.getByTestId('legal-support'));
    expect(open.mock.calls.map(([url]) => url)).toEqual([
      'https://dukkan.example/impressum',
      'https://dukkan.example/datenschutz',
      'mailto:hilfe@dukkan.example',
    ]);
  });

  it('registers only after the terms are accepted and sends the accepted version', async () => {
    const fake = createFakeApiClient({ ...base, 'GET /platform': PLATFORM, 'POST /auth/register': signedIn, 'GET /me': ada });
    await renderApp(createTestDependencies(fake.client));
    const user = await openProfile();
    await user.press(await screen.findByRole('tab', { name: 'Registrieren' }));
    await user.type(screen.getByLabelText('Vorname'), 'Ada');
    await user.type(screen.getByLabelText('Nachname'), 'L');
    await user.type(screen.getByLabelText('E-Mail'), 'ada@example.de');
    await user.type(screen.getByLabelText('Passwort'), 'correct-horse-battery');

    await user.press(screen.getByTestId('auth-submit'));
    expect(await screen.findByText('Bitte akzeptiere die AGB und die Datenschutzerklärung.')).toBeOnTheScreen();
    expect(fake.calls.some((c) => c.path === '/auth/register')).toBe(false);

    await user.press(screen.getByRole('checkbox', { name: 'Ich akzeptiere die AGB und habe die Datenschutzerklärung gelesen.' }));
    await user.press(screen.getByTestId('auth-submit'));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({ path: '/auth/register', body: expect.objectContaining({ acceptedTermsVersion: '2026-11' }) as unknown }),
      ),
    );
  });

  it('shows no consent and no legal section while the operator has set nothing', async () => {
    const none: Platform = { legal: { imprintUrl: null, privacyUrl: null, termsUrl: null, termsVersion: null }, supportEmail: null, payment: 'SIMULATED' };
    await renderApp(createTestDependencies(createFakeApiClient({ ...base, 'GET /platform': none }).client));
    const user = await openProfile();
    await user.press(await screen.findByRole('tab', { name: 'Registrieren' }));
    expect(screen.queryByTestId('terms-consent')).not.toBeOnTheScreen();
    expect(screen.queryByTestId('legal-links')).not.toBeOnTheScreen();
  });
});
