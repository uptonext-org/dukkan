import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { catalogProduct, eur, marketDetail, MARKET_ID, order, page } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { AddProductScreen } from '../screens/AddProductScreen';
import { MerchantOrdersScreen } from '../screens/MerchantOrdersScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();
const merchantMarket = { ...marketDetail(), status: 'ACTIVE' as const };
const ayran = catalogProduct();
const listed = catalogProduct({ id: 'listed', name: 'Simit', alreadyListed: true });

function setup(extra: Parameters<typeof createFakeApiClient>[0] = {}) {
  const fake = createFakeApiClient({
    'GET /me': { id: 'm1', email: 'merchant@example.de', firstName: 'Mert', lastName: 'K', roles: ['MERCHANT'] },
    'GET /merchant/markets': [merchantMarket],
    'GET /cuisines': [
      { code: 'TURKISH', name: 'Türkisch' },
      { code: 'ARABIC', name: 'Arabisch' },
    ],
    'GET /merchant/catalog': page([ayran, listed]),
    [`GET /markets/${MARKET_ID}/categories`]: [{ id: 'cat-drinks', parentId: null, slug: 'getraenke', name: 'Getränke', sortOrder: 1 }],
    [`POST /merchant/markets/${MARKET_ID}/listings`]: { ...ayran, marketId: MARKET_ID, price: eur(129), available: true, organic: false, isNew: true, maxOrderQuantity: 24, privateProduct: false },
    ...extra,
  });
  return { fake, dependencies: createTestDependencies(fake.client, { token: 'merchant-jwt' }) };
}

function renderMerchantScreen(name: 'MerchantAddProduct' | 'MerchantOrders', dependencies: ReturnType<typeof setup>['dependencies']) {
  return renderInAppRoot(
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {name === 'MerchantAddProduct' ? (
        <Stack.Screen name="MerchantAddProduct" component={AddProductScreen} initialParams={{ marketId: MARKET_ID }} />
      ) : (
        <Stack.Screen name="MerchantOrders" component={MerchantOrdersScreen} initialParams={{ marketId: MARKET_ID }} />
      )}
    </Stack.Navigator>,
    dependencies,
  );
}

describe('Mein Markt: Produkt hinzufügen', () => {
  it('adds a listing from the shared catalog, filtered by cuisine', async () => {
    const { fake, dependencies } = setup();
    await renderMerchantScreen('MerchantAddProduct', dependencies);
    const user = userEvent.setup();

    await screen.findByTestId(`catalog-${ayran.id}`);
    expect(within(screen.getByTestId('catalog-listed')).getByText('Schon im Sortiment')).toBeOnTheScreen();

    await user.press(screen.getByTestId('cuisine-filter-TURKISH'));
    await waitFor(() => expect(fake.calls.filter((c) => c.path === '/merchant/catalog').at(-1)?.query).toMatchObject({ marketId: MARKET_ID, cuisine: 'TURKISH' }));
    await user.type(screen.getByTestId('catalog-search'), 'ayr');
    await waitFor(() => expect(fake.calls.filter((c) => c.path === '/merchant/catalog').at(-1)?.query).toMatchObject({ q: 'ayr', cuisine: 'TURKISH' }));

    await user.press(within(await screen.findByTestId(`catalog-${ayran.id}`)).getByRole('button', { name: 'Ayran wählen' }));
    // Invalid price is caught on the device.
    await user.type(screen.getByTestId(`catalog-price-${ayran.id}`), '1,299');
    await user.press(screen.getByTestId(`catalog-add-${ayran.id}`));
    expect(await screen.findByText('Bitte einen Betrag wie 2,49 eingeben.')).toBeOnTheScreen();
    expect(fake.calls.some((c) => c.method === 'POST')).toBe(false);

    await user.clear(screen.getByTestId(`catalog-price-${ayran.id}`));
    await user.type(screen.getByTestId(`catalog-price-${ayran.id}`), '1,29');
    await user.press(screen.getByTestId(`catalog-add-${ayran.id}`));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({
          method: 'POST',
          path: `/merchant/markets/${MARKET_ID}/listings`,
          body: { catalogProductId: ayran.id, newProduct: null, price: { amountCents: 129, currency: 'EUR' }, available: true, maxOrderQuantity: 24 },
        }),
      ),
    );
    expect(await screen.findByTestId('listing-added')).toHaveTextContent('Produkt hinzugefügt.');
  });

  it('validates the create-product form before sending, then sends names in de/en/ar and keywords', async () => {
    const { fake, dependencies } = setup();
    await renderMerchantScreen('MerchantAddProduct', dependencies);
    const user = userEvent.setup();
    await user.press(await screen.findByRole('tab', { name: 'Neues Produkt' }));
    await screen.findByTestId('new-product-form');

    await user.press(screen.getByTestId('new-product-submit'));
    expect(await screen.findByText('Bitte mindestens in der Hauptsprache ausfüllen.')).toBeOnTheScreen();
    expect(screen.getByText('Bitte wähl eine Kategorie.')).toBeOnTheScreen();
    expect(screen.getAllByText('Bitte ausfüllen.').length).toBeGreaterThanOrEqual(2);
    expect(fake.calls.some((c) => c.method === 'POST')).toBe(false);

    await user.type(screen.getByTestId('new-name-de'), 'Knoblauchwurst');
    await user.type(screen.getByTestId('new-name-ar'), 'سجق');
    await user.type(screen.getByLabelText('Schlagwörter'), 'sucuk, garlic sausage');
    await user.type(screen.getByTestId('new-unit'), '250 g');
    await user.press(screen.getByRole('button', { name: 'Getränke' }));
    await user.type(screen.getByTestId('new-price'), '4,99');
    await user.press(screen.getByTestId('new-product-submit'));

    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({
          method: 'POST',
          body: expect.objectContaining({
            catalogProductId: null,
            newProduct: expect.objectContaining({ name: { de: 'Knoblauchwurst', ar: 'سجق' }, keywords: ['sucuk', 'garlic sausage'], unitSize: '250 g', categoryId: 'cat-drinks' }),
            price: { amountCents: 499, currency: 'EUR' },
          }),
        }),
      ),
    );
  });
});

describe('Mein Markt: Bestellungen', () => {
  it('shows status buttons per the allowed transitions and advances the order', async () => {
    const placed = order();
    const { fake, dependencies } = setup({
      [`GET /merchant/markets/${MARKET_ID}/orders`]: [placed, order({ id: 'o2', orderNumber: 'DK-2', status: 'ACCEPTED', fulfilmentType: 'PICKUP', customerName: 'Bo' })],
      [`POST /merchant/orders/${placed.id}/status`]: { ...placed, status: 'ACCEPTED' },
    });
    await renderMerchantScreen('MerchantOrders', dependencies);
    const user = userEvent.setup();

    const first = await screen.findByTestId(`merchant-order-${placed.id}`);
    expect(within(first).getByRole('button', { name: 'Annehmen' })).toBeOnTheScreen();
    expect(within(first).getByRole('button', { name: 'Ablehnen' })).toBeOnTheScreen();
    const second = screen.getByTestId('merchant-order-o2');
    expect(within(second).getByRole('button', { name: 'Bereit zur Abholung' })).toBeOnTheScreen();
    expect(within(second).queryByRole('button', { name: 'Unterwegs' })).not.toBeOnTheScreen();

    await user.press(within(first).getByRole('button', { name: 'Annehmen' }));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'POST', path: `/merchant/orders/${placed.id}/status`, body: { status: 'ACCEPTED' } })),
    );
  });
});
