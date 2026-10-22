import { fireEvent, screen, userEvent, waitFor } from '@testing-library/react-native';

import { ApiError, type AppNotification, type NotificationPage } from '../../../shared/api';
import { createFakePushRegistration } from '../../../shared/push';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const base = { 'GET /market-types': [], 'GET /cuisines': [], 'GET /markets': page([market()]), 'GET /carts': [] };
const merchant = { id: 'm1', email: 'mert@example.de', firstName: 'Mert', lastName: 'K', roles: ['CUSTOMER' as const, 'MERCHANT' as const] };

function notification(overrides: Partial<AppNotification>): AppNotification {
  return { id: 'n1', kind: 'NEW_ORDER', title: 'Neue Bestellung', body: 'DK-20261021-000042 · 23,40 € · Abholung', orderId: 'o1', marketId: 'mk1', createdAt: '2026-10-21T08:15:00Z', read: false, ...overrides };
}

async function openProfile() {
  const user = userEvent.setup();
  await user.press(await screen.findByRole('button', { name: 'Profil, Tab 4 von 4' }));
  return user;
}

describe('Mitteilungen (B-37)', () => {
  it('shows the unread count, lists notifications, marks them read and opens the merchant orders', async () => {
    let inbox: NotificationPage = { ...page([notification({}), notification({ id: 'n2', kind: 'ORDER_ACCEPTED', title: 'Bestellung angenommen', body: 'Anatolia · DK-1', read: true })]), unreadCount: 1 };
    const fake = createFakeApiClient({
      ...base,
      'GET /me': merchant,
      'GET /me/notifications': () => inbox,
      'POST /me/notifications/read': () => {
        inbox = { ...inbox, items: inbox.items.map((n) => ({ ...n, read: true })), unreadCount: 0 };
        return undefined;
      },
      'GET /merchant/markets/mk1/orders': [],
      'GET /merchant/markets': [],
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = await openProfile();

    await waitFor(() => expect(screen.getByTestId('profile-notifications')).toHaveAccessibleName('Mitteilungen, 1 neu'));
    await user.press(screen.getByTestId('profile-notifications'));
    expect(await screen.findByText('Neue Bestellung')).toBeOnTheScreen();
    expect(screen.getByText('DK-20261021-000042 · 23,40 € · Abholung')).toBeOnTheScreen();
    await waitFor(() => expect(fake.calls.some((c) => c.method === 'POST' && c.path === '/me/notifications/read')).toBe(true));

    await user.press(screen.getByTestId('notification-n1'));
    await waitFor(() => expect(fake.calls.some((c) => c.path === '/merchant/markets/mk1/orders')).toBe(true));
  });

  it('switches push off and back on; a failed save puts the switch back', async () => {
    let fail = false;
    const fake = createFakeApiClient({
      ...base,
      'GET /me': merchant,
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'GET /me/notification-settings': { pushEnabled: true },
      'PUT /me/notification-settings': (request) => {
        if (fail) {
          throw new ApiError({ kind: 'network', status: 0, code: 'NETWORK_ERROR' });
        }
        return request.body;
      },
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = await openProfile();
    await user.press(await screen.findByTestId('profile-notifications'));

    const toggle = await screen.findByTestId('push-setting');
    expect(toggle.props.value).toBe(true);
    await fireEvent(toggle, 'valueChange', false);
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'PUT', path: '/me/notification-settings', body: { pushEnabled: false } })));
    await waitFor(() => expect(screen.getByTestId('push-setting').props.value).toBe(false));

    fail = true;
    await fireEvent(screen.getByTestId('push-setting'), 'valueChange', true);
    expect(await screen.findByRole('alert')).toBeOnTheScreen();
    await waitFor(() => expect(screen.getByTestId('push-setting').props.value).toBe(false));
  });

  it('registers the device for push after sign-in and removes it on logout', async () => {
    const user = { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER' as const] };
    const fake = createFakeApiClient({
      ...base,
      'POST /auth/login': { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'r1', refreshExpiresIn: 100, user },
      'GET /me': user,
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'PUT /me/devices': () => undefined,
      'DELETE /me/devices': () => undefined,
      'POST /auth/logout': () => undefined,
    });
    await renderApp(createTestDependencies(fake.client, { pushRegistration: createFakePushRegistration('fcm-token-1', 'IOS') }));
    const press = await openProfile();

    await press.type(await screen.findByLabelText('E-Mail'), 'ada@example.de');
    await press.type(screen.getByLabelText('Passwort'), 'secret-password');
    await press.press(screen.getByTestId('auth-submit'));
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'PUT', path: '/me/devices', body: { token: 'fcm-token-1', platform: 'IOS' } })));

    await press.press(await screen.findByRole('button', { name: 'Abmelden' }));
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'DELETE', path: '/me/devices', body: { token: 'fcm-token-1' } })));
    const order = fake.calls.map((c) => `${c.method} ${c.path}`);
    expect(order.indexOf('DELETE /me/devices')).toBeLessThan(order.indexOf('POST /auth/logout'));
  });
});
