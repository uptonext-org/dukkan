import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, userEvent, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';

import type { RootStackParamList, RootStackScreenProps } from '../../../navigation/types';
import type { ReorderResult } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { order } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { OrderDetailScreen } from '../screens/OrderDetailScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

function OpenedMarket({ route }: RootStackScreenProps<'Market'>) {
  return <Text testID="opened-market">{JSON.stringify(route.params)}</Text>;
}

async function openOrder(result: ReorderResult) {
  const past = order({ status: 'COMPLETED' });
  const fake = createFakeApiClient({ [`GET /orders/${past.id}`]: past, [`POST /orders/${past.id}/reorder`]: result });
  await renderInAppRoot(
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="OrderDetail" component={OrderDetailScreen} initialParams={{ orderId: past.id }} />
      <Stack.Screen name="Market" component={OpenedMarket} />
    </Stack.Navigator>,
    createTestDependencies(fake.client, { token: 'jwt' }),
  );
  const user = userEvent.setup();
  await user.press(await screen.findByTestId('reorder'));
  return { fake, user, past };
}

describe('Nochmal bestellen', () => {
  it('goes straight to the cart when everything was added', async () => {
    const { fake, past } = await openOrder({ marketId: 'm1', addedProducts: 1, skipped: [] });
    expect(await screen.findByTestId('opened-market')).toHaveTextContent('"marketId":"m1"', { exact: false });
    expect(screen.getByTestId('opened-market')).toHaveTextContent('MarketCart', { exact: false });
    await waitFor(() => expect(fake.calls.some((c) => c.method === 'POST' && c.path === `/orders/${past.id}/reorder`)).toBe(true));
  });

  it('names what could not be added and then offers the cart', async () => {
    const { user } = await openOrder({
      marketId: 'm1',
      addedProducts: 1,
      skipped: [
        { productId: 'p2', name: 'Ayran', reason: 'UNAVAILABLE' },
        { productId: 'p3', name: 'Simit', reason: 'REMOVED' },
      ],
    });
    expect(await screen.findByText('Nicht alles ist im Warenkorb')).toBeOnTheScreen();
    expect(screen.getByText('Ayran – gerade nicht verfügbar')).toBeOnTheScreen();
    expect(screen.getByText('Simit – nicht mehr im Sortiment')).toBeOnTheScreen();
    expect(screen.queryByTestId('reorder')).toBeNull();
    await user.press(screen.getByTestId('reorder-to-cart'));
    expect(await screen.findByTestId('opened-market')).toBeOnTheScreen();
  });

  it('says so when nothing is available', async () => {
    await openOrder({ marketId: 'm1', addedProducts: 0, skipped: [{ productId: 'p2', name: 'Ayran', reason: 'UNAVAILABLE' }] });
    expect(await screen.findByText('Diese Produkte gibt es gerade nicht.')).toBeOnTheScreen();
    expect(screen.queryByTestId('reorder-to-cart')).toBeNull();
  });
});
