import { createNativeStackNavigator, type NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect } from 'react';
import { screen, userEvent, waitFor } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import type { ApiRequest, Listing, ListingDetails, UpdateListingRequest } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { eur, marketDetail, MARKET_ID, page, product } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { EditListingScreen } from '../screens/EditListingScreen';
import { ListingsScreen } from '../screens/ListingsScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

const ownListing: Listing = { ...product({ id: 'own-1', name: 'mata', unitSize: '500 g', price: eur(300), maxOrderQuantity: 24 }), privateProduct: true };
const ownDetails: ListingDetails = {
  listing: ownListing,
  product: {
    name: { de: 'mata', ar: 'ماتا' },
    keywords: [],
    brand: null,
    unitSize: '500 g',
    categoryId: 'cat-deli',
    cuisineCodes: [],
    organic: false,
    allergens: [],
    barcode: null,
  },
};
const catalogListing: Listing = { ...product({ id: 'cat-1', name: 'Ayran', unitSize: '250 ml', brand: 'Yayla', price: eur(99) }), privateProduct: false };
const catalogDetails: ListingDetails = {
  listing: catalogListing,
  product: { name: { de: 'Ayran', ar: 'عيران' }, brand: 'Yayla', unitSize: '250 ml', categoryId: 'cat-drinks' },
};

function handlers(details: ListingDetails) {
  return {
    'GET /me': { id: 'm1', email: 'merchant@example.de', firstName: 'Mert', lastName: 'K', roles: ['MERCHANT'] },
    'GET /merchant/markets': [{ ...marketDetail(), status: 'ACTIVE' as const }],
    'GET /cuisines': [{ code: 'ARABIC', name: 'Arabisch' }],
    'GET /categories': [
      { id: 'cat-deli', parentId: null, slug: 'deli', name: 'Feinkost', sortOrder: 1, productCount: null },
      { id: 'cat-spices', parentId: null, slug: 'spices', name: 'Gewürze', sortOrder: 2, productCount: null },
    ],
    [`GET /merchant/markets/${MARKET_ID}/listings`]: page([details.listing]),
    [`GET /merchant/markets/${MARKET_ID}/listings/${details.listing.id}/details`]: details,
    [`PUT /merchant/markets/${MARKET_ID}/listings/${details.listing.id}`]: (request: ApiRequest) => ({ ...details.listing, ...(request.body as object) }),
  };
}

/** The product list first, so that saving (goBack) has somewhere to return to. */
function OpenEdit({ navigation, listingId }: NativeStackScreenProps<RootStackParamList, 'MerchantListings'> & { listingId: string }) {
  useEffect(() => navigation.navigate('MerchantEditListing', { marketId: MARKET_ID, listingId }), [navigation, listingId]);
  return null;
}

async function renderEdit(details: ListingDetails) {
  const fake = createFakeApiClient(handlers(details));
  await renderInAppRoot(
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MerchantListings" initialParams={{ marketId: MARKET_ID }}>
        {(props) => <OpenEdit {...props} listingId={details.listing.id} />}
      </Stack.Screen>
      <Stack.Screen name="MerchantEditListing" component={EditListingScreen} />
    </Stack.Navigator>,
    createTestDependencies(fake.client, { token: 'merchant-jwt' }),
  );
  return fake;
}

const sentUpdate = (fake: Awaited<ReturnType<typeof renderEdit>>) => fake.calls.find((c) => c.method === 'PUT')?.body as UpdateListingRequest | undefined;

describe('Mein Markt: Produkt bearbeiten', () => {
  it('opens the full edit screen from the pencil in the product list', async () => {
    const fake = createFakeApiClient(handlers(ownDetails));
    await renderInAppRoot(
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="MerchantListings" component={ListingsScreen} initialParams={{ marketId: MARKET_ID }} />
        <Stack.Screen name="MerchantEditListing" component={EditListingScreen} />
      </Stack.Navigator>,
      createTestDependencies(fake.client, { token: 'merchant-jwt' }),
    );
    await userEvent.setup().press(await screen.findByTestId('edit-own-1'));
    expect(await screen.findByTestId('edit-own-product')).toBeOnTheScreen();
    expect(screen.getByTestId('edit-name-de')).toHaveDisplayValue('mata');
  });

  it('changes every detail of an own product, not only the price', async () => {
    const fake = await renderEdit(ownDetails);
    const user = userEvent.setup();

    const name = await screen.findByTestId('edit-name-de');
    await user.clear(name);
    await user.type(name, 'Matbucha');
    await user.type(screen.getByTestId('edit-brand'), 'Hausgemacht');
    await user.clear(screen.getByTestId('edit-unit'));
    await user.type(screen.getByTestId('edit-unit'), '400 g');
    await user.press(screen.getByTestId('edit-category-cat-spices'));
    await user.press(screen.getByTestId('edit-allergen-celery'));
    await user.type(screen.getByTestId('edit-barcode'), '4006381333931');
    await user.clear(screen.getByTestId('edit-price'));
    await user.type(screen.getByTestId('edit-price'), '3,49');
    await user.press(screen.getByTestId('edit-save'));

    await waitFor(() => expect(sentUpdate(fake)).toBeDefined());
    expect(sentUpdate(fake)?.product?.name).toEqual({ de: 'Matbucha' });
    expect(sentUpdate(fake)).toMatchObject({
      product: {
        // Only the market's language is sent; the server keeps or rewrites the others.
        name: { de: 'Matbucha' },
        brand: 'Hausgemacht',
        unitSize: '400 g',
        categoryId: 'cat-spices',
        allergens: ['celery'],
        barcode: '4006381333931',
      },
      price: eur(349),
      maxOrderQuantity: 24,
    });
  });

  it('rejects a barcode that is not 8 to 14 digits', async () => {
    const fake = await renderEdit(ownDetails);
    const user = userEvent.setup();
    await user.type(await screen.findByTestId('edit-barcode'), '123');
    await user.press(screen.getByTestId('edit-save'));
    expect(await screen.findByText('Ein Barcode hat 8 bis 14 Ziffern.')).toBeOnTheScreen();
    expect(sentUpdate(fake)).toBeUndefined();
  });

  it('adjusts a shared catalog product with own details for this market only', async () => {
    const fake = await renderEdit(catalogDetails);
    const user = userEvent.setup();

    expect(await screen.findByTestId('edit-catalog-listing')).toBeOnTheScreen();
    expect(screen.getByText('Katalog: Ayran')).toBeOnTheScreen();
    await user.type(screen.getByTestId('edit-own-name'), 'Hausgemachter Ayran');
    await user.type(screen.getByTestId('edit-own-unit'), '500 ml');
    await user.press(screen.getByTestId('edit-save'));

    await waitFor(() => expect(sentUpdate(fake)).toBeDefined());
    expect(sentUpdate(fake)).toMatchObject({
      overrides: { name: 'Hausgemachter Ayran', unitSize: '500 ml', description: null, brand: null },
      price: eur(99),
    });
    expect(sentUpdate(fake)).not.toHaveProperty('product');
  });
});
