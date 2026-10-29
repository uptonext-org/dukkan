import { createNativeStackNavigator, type NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect } from 'react';
import { screen, userEvent, waitFor } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import { ApiError, type ApiRequest, type Listing, type MediaUpload } from '../../../shared/api';
import { createFakePhotoPicker, type PhotoPickResult } from '../../../shared/media';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { readForm } from '../../../test/formData';
import { marketDetail, MARKET_ID, page, product } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { AddProductScreen } from '../screens/AddProductScreen';
import { ListingsScreen } from '../screens/ListingsScreen';
import { MarketProfileFormScreen } from '../screens/MarketProfileFormScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();
const MEDIA_PATH = `/merchant/markets/${MARKET_ID}/media`;
const PHOTO_URL = `http://localhost:8080/api/v1/media/${'a'.repeat(64)}.jpg`;
const picked: PhotoPickResult = {
  status: 'picked',
  photo: { uri: 'file:///data/user/0/app.dukkan.mobile/cache/rn_image_picker_lib_temp_1.jpg', type: 'image/jpeg', fileName: 'apfel.jpg', width: 2048, height: 1536 },
};
const uploaded: MediaUpload = { key: `${'a'.repeat(64)}.jpg`, url: PHOTO_URL, width: 2048, height: 1536, contentType: 'image/jpeg', sizeBytes: 412_345 };

function baseHandlers(listing: { current: Listing }) {
  return {
    'GET /me': { id: 'm1', email: 'merchant@example.de', firstName: 'Mert', lastName: 'K', roles: ['MERCHANT'] },
    'GET /merchant/markets': [{ ...marketDetail({ logoUrl: null, coverImageUrl: null, phone: '+49 40 1234567' }), status: 'ACTIVE' as const }],
    'GET /cuisines': [{ code: 'TURKISH', name: 'Türkisch' }],
    'GET /market-types': [{ code: 'GROCERY', name: 'Lebensmittel' }],
    'GET /categories': [{ id: 'cat-fruit', parentId: null, slug: 'obst', name: 'Obst', sortOrder: 1, productCount: null }],
    'GET /merchant/catalog': page([]),
    [`GET /merchant/markets/${MARKET_ID}/listings`]: () => page([listing.current]),
    [`PUT /merchant/markets/${MARKET_ID}/listings/${listing.current.id}`]: (request: ApiRequest) => {
      const body = request.body as { imageUrl?: string | null };
      listing.current = { ...listing.current, imageUrl: body.imageUrl ?? null };
      return listing.current;
    },
  };
}

function formPart(request: ApiRequest | undefined, name: string): unknown {
  expect(request?.body).toBeInstanceOf(FormData);
  return readForm(request?.body).get(name);
}

/** A first screen so that the form's goBack() after saving has somewhere to go. */
function OpenForm({ navigation }: NativeStackScreenProps<RootStackParamList, 'MerchantMarkets'>) {
  useEffect(() => navigation.navigate('MerchantMarketForm', { marketId: MARKET_ID }), [navigation]);
  return null;
}

function renderScreen(name: 'MerchantListings' | 'MerchantAddProduct' | 'MerchantMarketForm', dependencies: ReturnType<typeof createTestDependencies>) {
  return renderInAppRoot(
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {name === 'MerchantListings' && <Stack.Screen name="MerchantListings" component={ListingsScreen} initialParams={{ marketId: MARKET_ID }} />}
      {name === 'MerchantAddProduct' && <Stack.Screen name="MerchantAddProduct" component={AddProductScreen} initialParams={{ marketId: MARKET_ID }} />}
      {name === 'MerchantMarketForm' && <Stack.Screen name="MerchantMarkets" component={OpenForm} />}
      {name === 'MerchantMarketForm' && <Stack.Screen name="MerchantMarketForm" component={MarketProfileFormScreen} initialParams={{ marketId: MARKET_ID }} />}
    </Stack.Navigator>,
    dependencies,
  );
}

describe('Mein Markt: Fotos', () => {
  it('uploads a photo for an existing listing, shows it, and removes it again', async () => {
    const listing = { current: { ...product({ imageUrl: null }), privateProduct: false } as Listing };
    const picker = createFakePhotoPicker(picked);
    const fake = createFakeApiClient({ ...baseHandlers(listing), [`POST ${MEDIA_PATH}`]: uploaded });
    const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: picker });
    await renderScreen('MerchantListings', dependencies);
    const user = userEvent.setup();

    await user.press(await screen.findByTestId(`photo-toggle-${listing.current.id}`));
    expect(screen.queryByTestId(`listing-photo-${listing.current.id}-remove`)).not.toBeOnTheScreen();
    await user.press(screen.getByRole('button', { name: 'Produktfoto: Foto wählen' }));

    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'PUT', body: { imageUrl: PHOTO_URL } })));
    expect(picker.calls).toEqual(['library']);
    const upload = fake.calls.find((c) => c.method === 'POST' && c.path === MEDIA_PATH);
    expect(formPart(upload, 'purpose')).toBe('PRODUCT');
    expect(formPart(upload, 'file')).not.toBeNull();
    // The refetched listing carries the photo; now it can be removed (sets null).
    await user.press(await screen.findByTestId(`listing-photo-${listing.current.id}-remove`));
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'PUT', body: { imageUrl: null } })));
    await waitFor(() => expect(screen.queryByTestId(`listing-photo-${listing.current.id}-remove`)).not.toBeOnTheScreen());
  });

  it('shows the translated server error when the upload is rejected and does not change the listing', async () => {
    const listing = { current: { ...product(), privateProduct: false } as Listing };
    const fake = createFakeApiClient({
      ...baseHandlers(listing),
      [`POST ${MEDIA_PATH}`]: () => {
        throw new ApiError({ kind: 'http', status: 413, code: 'FILE_TOO_LARGE', detail: 'too large' });
      },
    });
    const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: createFakePhotoPicker(picked), language: 'en' });
    await renderScreen('MerchantListings', dependencies);
    const user = userEvent.setup();

    await user.press(await screen.findByTestId(`photo-toggle-${listing.current.id}`));
    await user.press(screen.getByRole('button', { name: 'Product photo: choose photo' }));
    expect(await screen.findByTestId(`listing-photo-${listing.current.id}-error`)).toHaveTextContent('The photo is too large (5 MB at most).');
    expect(fake.calls.some((c) => c.method === 'PUT')).toBe(false);
  });

  it('explains a denied permission and ignores a cancelled picker', async () => {
    const listing = { current: { ...product(), privateProduct: false } as Listing };
    const picker = createFakePhotoPicker({ status: 'cancelled' }, { status: 'failed', reason: 'permission' });
    const fake = createFakeApiClient({ ...baseHandlers(listing), [`POST ${MEDIA_PATH}`]: uploaded });
    const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: picker, language: 'ar' });
    await renderScreen('MerchantListings', dependencies);
    const user = userEvent.setup();

    await user.press(await screen.findByTestId(`photo-toggle-${listing.current.id}`));
    await user.press(screen.getByTestId(`listing-photo-${listing.current.id}-choose`));
    expect(screen.queryByTestId(`listing-photo-${listing.current.id}-error`)).not.toBeOnTheScreen();
    await user.press(screen.getByTestId(`listing-photo-${listing.current.id}-camera`));
    expect(await screen.findByTestId(`listing-photo-${listing.current.id}-error`)).toHaveTextContent('لا يوجد إذن بالوصول إلى الصور أو الكاميرا. يُرجى السماح بذلك من الإعدادات.');
    expect(picker.calls).toEqual(['library', 'camera']);
    expect(fake.calls.some((c) => c.path === MEDIA_PATH)).toBe(false);
  });

  it('attaches the uploaded photo to a new product', async () => {
    const listing = { current: { ...product(), privateProduct: true } as Listing };
    const fake = createFakeApiClient({
      ...baseHandlers(listing),
      [`POST ${MEDIA_PATH}`]: uploaded,
      [`POST /merchant/markets/${MARKET_ID}/listings`]: (request: ApiRequest) => ({ ...listing.current, imageUrl: (request.body as { newProduct: { imageUrl?: string } }).newProduct.imageUrl ?? null }),
    });
    const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: createFakePhotoPicker(picked) });
    await renderScreen('MerchantAddProduct', dependencies);
    const user = userEvent.setup();
    await user.press(await screen.findByRole('tab', { name: 'Neues Produkt' }));

    await user.press(await screen.findByTestId('new-photo-choose'));
    await screen.findByTestId('new-photo-remove');
    await user.type(screen.getByTestId('new-name-de'), 'Hausgemachtes Ajvar');
    await user.type(screen.getByTestId('new-unit'), '300 g');
    await user.press(screen.getByRole('button', { name: 'Obst' }));
    await user.type(screen.getByTestId('new-price'), '4,50');
    await user.press(screen.getByTestId('new-product-submit'));

    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({
          method: 'POST',
          path: `/merchant/markets/${MARKET_ID}/listings`,
          body: expect.objectContaining({ newProduct: expect.objectContaining({ imageUrl: PHOTO_URL }) }),
        }),
      ),
    );
    expect(formPart(fake.calls.find((c) => c.path === MEDIA_PATH), 'purpose')).toBe('PRODUCT');
  });

  it('uploads logo and cover in the market profile form and saves their URLs', async () => {
    const listing = { current: { ...product(), privateProduct: false } as Listing };
    const coverUrl = `http://localhost:8080/api/v1/media/${'b'.repeat(64)}.png`;
    let uploads = 0;
    const fake = createFakeApiClient({
      ...baseHandlers(listing),
      [`POST ${MEDIA_PATH}`]: () => (++uploads === 1 ? uploaded : { ...uploaded, key: `${'b'.repeat(64)}.png`, url: coverUrl, contentType: 'image/png' }),
      [`PUT /merchant/markets/${MARKET_ID}`]: (request: ApiRequest) => ({ ...marketDetail(), ...(request.body as object), status: 'ACTIVE' }),
    });
    const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: createFakePhotoPicker(picked) });
    await renderScreen('MerchantMarketForm', dependencies);
    const user = userEvent.setup();

    await user.press(await screen.findByRole('button', { name: 'Logo: Foto wählen' }));
    await screen.findByTestId('logo-photo-remove');
    await user.press(screen.getByRole('button', { name: 'Titelfoto: Foto wählen' }));
    await screen.findByTestId('cover-photo-remove');
    const purposes = fake.calls.filter((c) => c.path === MEDIA_PATH).map((c) => formPart(c, 'purpose'));
    expect(purposes).toEqual(['LOGO', 'COVER']);

    await user.press(screen.getByTestId('market-save'));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({ method: 'PUT', path: `/merchant/markets/${MARKET_ID}`, body: expect.objectContaining({ logoUrl: PHOTO_URL, coverImageUrl: coverUrl }) }),
      ),
    );
  });

  it('edits opening hours with a second range and a range past midnight', async () => {
    const listing = { current: { ...product(), privateProduct: false } as Listing };
    const fake = createFakeApiClient({
      ...baseHandlers(listing),
      [`PUT /merchant/markets/${MARKET_ID}`]: (request: ApiRequest) => ({ ...marketDetail(), ...(request.body as object), status: 'ACTIVE' }),
    });
    const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: createFakePhotoPicker(picked) });
    await renderScreen('MerchantMarketForm', dependencies);
    const user = userEvent.setup();

    await user.press(await screen.findByTestId('hours-add-1'));
    await user.clear(screen.getByTestId('hours-1-0-closes'));
    await user.type(screen.getByTestId('hours-1-0-closes'), '12:00');
    await user.clear(screen.getByTestId('hours-1-1-opens'));
    await user.type(screen.getByTestId('hours-1-1-opens'), '18:00');
    await user.clear(screen.getByTestId('hours-1-1-closes'));
    await user.type(screen.getByTestId('hours-1-1-closes'), '02:00');
    expect(screen.getByTestId('hours-1-1-next-day')).toHaveTextContent('bis zum nächsten Tag');

    await user.press(screen.getByTestId('market-save'));
    await waitFor(() => expect(fake.calls.some((c) => c.method === 'PUT')).toBe(true));
    const body = fake.calls.find((c) => c.method === 'PUT' && c.path === `/merchant/markets/${MARKET_ID}`)?.body as { openingHours: unknown[] };
    expect(body.openingHours).toEqual(
      expect.arrayContaining([
        { dayOfWeek: 1, opensAt: '08:00', closesAt: '12:00' },
        { dayOfWeek: 1, opensAt: '18:00', closesAt: '02:00' },
      ]),
    );
    expect(body.openingHours.filter((h) => (h as { dayOfWeek: number }).dayOfWeek === 1)).toHaveLength(2);
  });
});
