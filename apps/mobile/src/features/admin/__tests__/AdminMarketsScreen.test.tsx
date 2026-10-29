import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { marketDetail, MARKET_ID } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { AdminMarketsScreen } from '../screens/AdminMarketsScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();
const review = (complete: boolean) => ({
  complete,
  items: [
    { code: 'LEGAL_NAME', ok: true, required: true },
    { code: 'POSTAL_ADDRESS', ok: true, required: true },
    { code: 'PHONE', ok: complete, required: true },
    { code: 'REGISTER_INFO', ok: false, required: false },
  ],
});
const pending = { ...marketDetail(), status: 'PENDING_REVIEW' as const, review: review(true) };

async function renderScreen(handlers: Parameters<typeof createFakeApiClient>[0]) {
  const fake = createFakeApiClient({
    'GET /me': { id: 'a1', email: 'admin@example.de', firstName: 'Ada', lastName: 'Admin', roles: ['ADMIN', 'CUSTOMER'] },
    ...handlers,
  });
  const dependencies = createTestDependencies(fake.client, { token: 'admin-jwt' });
  await renderInAppRoot(
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="AdminMarkets" component={AdminMarketsScreen} />
    </Stack.Navigator>,
    dependencies,
  );
  return { fake };
}

describe('Marktprüfung', () => {
  it('lists markets in review and approves one with a click', async () => {
    const { fake } = await renderScreen({
      'GET /admin/markets': [pending],
      [`POST /admin/markets/${MARKET_ID}/status`]: { ...pending, status: 'ACTIVE' },
    });
    const user = userEvent.setup();

    const card = await screen.findByTestId(`admin-market-${MARKET_ID}`);
    expect(fake.calls.find((c) => c.path === '/admin/markets')?.query).toMatchObject({ status: 'PENDING_REVIEW' });
    expect(within(card).getByText('✓ Telefonnummer')).toBeOnTheScreen();

    await user.press(screen.getByTestId(`admin-approve-${MARKET_ID}`));
    await waitFor(() => expect(fake.calls.find((c) => c.method === 'POST')?.body).toEqual({ status: 'ACTIVE' }));
  });

  it('shows what is missing and does not let an incomplete market be approved', async () => {
    await renderScreen({ 'GET /admin/markets': [{ ...pending, review: review(false) }] });

    const card = await screen.findByTestId(`admin-market-${MARKET_ID}`);
    expect(within(card).getByText('✗ Telefonnummer')).toBeOnTheScreen();
    expect(within(card).getByText(/Unvollständig/)).toBeOnTheScreen();
    expect(screen.getByTestId(`admin-approve-${MARKET_ID}`)).toBeDisabled();
  });
});
