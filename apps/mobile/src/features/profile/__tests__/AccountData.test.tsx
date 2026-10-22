import { Share } from 'react-native';
import { screen, userEvent, waitFor } from '@testing-library/react-native';

import { ApiError, type ApiRequest } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const base = { 'GET /market-types': [], 'GET /cuisines': [], 'GET /markets': page([market()]), 'GET /carts': [] };
const ada = { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER' as const] };

async function openAccountData() {
  const user = userEvent.setup();
  await user.press(await screen.findByRole('button', { name: 'Profil, Tab 4 von 4' }));
  await user.press(await screen.findByTestId('profile-account-data'));
  return user;
}

describe('Meine Daten & Konto (B-51)', () => {
  it('shares the data export', async () => {
    const share = jest.spyOn(Share, 'share').mockResolvedValue({ action: 'sharedAction', activityType: undefined });
    const fake = createFakeApiClient({
      ...base,
      'GET /me': ada,
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'GET /me/export': { account: { email: 'ada@example.de' }, orders: [] },
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = await openAccountData();
    await user.press(screen.getByTestId('account-export'));
    await waitFor(() => expect(share).toHaveBeenCalledTimes(1));
    expect(share.mock.calls[0]?.[0]).toEqual(expect.objectContaining({ title: 'Meine Dukkan-Daten', message: expect.stringContaining('"ada@example.de"') }));
  });

  it('asks twice, explains a wrong password and signs out after deleting', async () => {
    const fake = createFakeApiClient({
      ...base,
      'GET /me': ada,
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'DELETE /me': (request: ApiRequest) => {
        if ((request.body as { password?: string }).password !== 'correct-horse-battery') {
          throw new ApiError({ kind: 'http', status: 400, code: 'VALIDATION_FAILED', fieldCodes: { password: 'Incorrect' } });
        }
        return undefined;
      },
      'POST /auth/logout': () => undefined,
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = await openAccountData();

    await user.type(screen.getByTestId('account-delete-password'), 'nope');
    await user.press(screen.getByTestId('account-delete'));
    expect(screen.getByText('Wirklich löschen? Das kann nicht rückgängig gemacht werden.')).toBeOnTheScreen();
    expect(fake.calls.some((c) => c.method === 'DELETE')).toBe(false);
    await user.press(screen.getByTestId('account-delete-confirm'));
    expect(await screen.findByText('Das aktuelle Passwort stimmt nicht.')).toBeOnTheScreen();

    await user.clear(screen.getByTestId('account-delete-password'));
    await user.type(screen.getByTestId('account-delete-password'), 'correct-horse-battery');
    await user.press(screen.getByTestId('account-delete'));
    await user.press(screen.getByTestId('account-delete-confirm'));
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'DELETE', path: '/me', body: { password: 'correct-horse-battery' } })));
    // Signed out: the profile offers sign-in again.
    expect(await screen.findByRole('tab', { name: 'Registrieren' })).toBeOnTheScreen();
  });

  it('explains why an account with open orders cannot be deleted yet', async () => {
    const fake = createFakeApiClient({
      ...base,
      'GET /me': ada,
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'DELETE /me': () => {
        throw new ApiError({ kind: 'http', status: 409, code: 'ACCOUNT_HAS_OPEN_ORDERS' });
      },
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = await openAccountData();
    await user.press(screen.getByTestId('account-delete'));
    await user.press(screen.getByTestId('account-delete-confirm'));
    expect(await screen.findByTestId('account-delete-error')).toHaveTextContent('Du hast noch offene Bestellungen.', { exact: false });
    // Without a password (Apple/Google accounts) the body is empty.
    expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'DELETE', path: '/me', body: {} }));
  });
});
