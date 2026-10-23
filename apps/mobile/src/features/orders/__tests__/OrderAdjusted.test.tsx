import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, within } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { eur, order } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { OrderDetailScreen } from '../screens/OrderDetailScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

describe('Fehlartikel in der Bestellung', () => {
  it('shows what the market could not hand over and the lower total', async () => {
    const adjusted = order({
      status: 'ACCEPTED',
      items: [
        { lineNo: 1, productId: 'p1', name: 'Salatgurke', unitPrice: eur(119), quantity: 2, fulfilledQuantity: null, lineTotal: eur(238) },
        { lineNo: 2, productId: 'p2', name: 'Ayran', unitPrice: eur(650), quantity: 3, fulfilledQuantity: 1, lineTotal: eur(650) },
        { lineNo: 3, productId: 'p3', name: 'Schwarzer Tee', unitPrice: eur(800), quantity: 1, fulfilledQuantity: 0, lineTotal: eur(0) },
      ],
      subtotal: eur(888),
      total: eur(888),
      originalTotal: eur(2988),
    });
    const fake = createFakeApiClient({ [`GET /orders/${adjusted.id}`]: adjusted });
    await renderInAppRoot(
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="OrderDetail" component={OrderDetailScreen} initialParams={{ orderId: adjusted.id }} />
      </Stack.Navigator>,
      createTestDependencies(fake.client, { token: 'jwt' }),
    );

    const notice = await screen.findByTestId('order-adjusted');
    expect(notice).toHaveTextContent(/Der Markt hat deine Bestellung angepasst/);
    expect(notice).toHaveTextContent(/.?2.? Artikel waren nicht \(ganz\) vorrätig/);
    expect(within(screen.getByTestId('order-line-2')).getByText(/^1 × Ayran · von .?3.? bestellt$/)).toBeOnTheScreen();
    expect(within(screen.getByTestId('order-line-3')).getByText('1 × Schwarzer Tee · nicht verfügbar')).toBeOnTheScreen();
    expect(within(screen.getByTestId('order-line-1')).getByText('2 × Salatgurke')).toBeOnTheScreen();
    expect(screen.getByText(/^statt .?29,88/)).toBeOnTheScreen();
  });
});
