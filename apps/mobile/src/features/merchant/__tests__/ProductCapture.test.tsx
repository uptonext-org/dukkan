import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import type { RootStackParamList } from '../../../navigation/types';
import type { ApiRequest, MediaUpload, ProductDraft } from '../../../shared/api';
import { createFakePhotoPicker } from '../../../shared/media';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { catalogProduct, eur, marketDetail, MARKET_ID, page } from '../../../test/fixtures';
import { createTestDependencies, renderInAppRoot } from '../../../test/render';
import { mediaKeyOf } from '../components/CapturePanel';
import { AddProductScreen } from '../screens/AddProductScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();
const DRAFT_PATH = `/merchant/markets/${MARKET_ID}/product-drafts`;
const KEY = `${'b'.repeat(64)}.jpg`;
const PHOTO_URL = `http://localhost:8080/api/v1/media/${KEY}`;
const sucuk = catalogProduct({ id: 'cat-sucuk', name: 'Sucuk', unitSize: '400 g', categoryId: 'cat-meat' });

const aiDraft: ProductDraft = {
  engine: 'AI',
  product: {
    name: { de: 'Sucuk', en: 'Sucuk beef sausage', ar: 'سجق تركي' },
    description: { de: 'Würzige Rinderwurst mit Knoblauch.' },
    keywords: ['sucuk', 'sujuk'],
    brand: 'Gazi',
    unitSize: '400 g',
    netQuantity: 0.4,
    netUnit: 'kg',
    categoryId: 'cat-meat',
    cuisineCodes: ['TURKISH'],
    organic: false,
    allergens: ['mustard'],
    imageUrl: null,
    barcode: '8690000000001',
  },
  price: eur(449),
  catalogMatches: [sucuk],
  warnings: [],
};

function setup(draft: ProductDraft | ((request: ApiRequest) => ProductDraft)) {
  const fake = createFakeApiClient({
    'GET /me': { id: 'm1', email: 'merchant@example.de', firstName: 'Mert', lastName: 'K', roles: ['MERCHANT'] },
    'GET /merchant/markets': [{ ...marketDetail(), status: 'ACTIVE' as const }],
    'GET /cuisines': [{ code: 'TURKISH', name: 'Türkisch' }],
    'GET /merchant/catalog': page([]),
    'GET /categories': [
      { id: 'cat-meat', parentId: null, slug: 'meat', name: 'Fleisch & Geflügel', sortOrder: 1, productCount: null },
      { id: 'cat-dairy', parentId: null, slug: 'dairy', name: 'Milch, Käse & Eier', sortOrder: 2, productCount: null },
    ],
    [`POST ${DRAFT_PATH}`]: draft,
    [`POST /merchant/markets/${MARKET_ID}/media`]: { key: KEY, url: PHOTO_URL, width: 800, height: 600, contentType: 'image/jpeg', sizeBytes: 90_000 } satisfies MediaUpload,
    [`POST /merchant/markets/${MARKET_ID}/listings`]: { ...sucuk, marketId: MARKET_ID, price: eur(449), available: true, organic: false, isNew: true, maxOrderQuantity: 24, privateProduct: true },
  });
  const picker = createFakePhotoPicker({
    status: 'picked',
    photo: { uri: 'file:///cache/pack.jpg', type: 'image/jpeg', fileName: 'pack.jpg', width: 800, height: 600 },
  });
  const dependencies = createTestDependencies(fake.client, { token: 'merchant-jwt', photoPicker: picker });
  return { fake, dependencies };
}

async function openNewProduct(dependencies: ReturnType<typeof setup>['dependencies']) {
  await renderInAppRoot(
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MerchantAddProduct" component={AddProductScreen} initialParams={{ marketId: MARKET_ID }} />
    </Stack.Navigator>,
    dependencies,
  );
  const user = userEvent.setup();
  await user.press(await screen.findByRole('tab', { name: 'Neues Produkt' }));
  return user;
}

describe('Mein Markt: Schnell erfassen', () => {
  it('fills the form from an AI draft, offers the catalog match and creates the product with all details', async () => {
    const { fake, dependencies } = setup(aiDraft);
    const user = await openNewProduct(dependencies);

    await user.type(await screen.findByTestId('capture-text'), 'Sucuk Gazi 400 g 4,49 €');
    await user.press(screen.getByTestId('capture-submit'));

    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'POST', path: DRAFT_PATH, body: { text: 'Sucuk Gazi 400 g 4,49 €' } })));
    expect(await screen.findByTestId('capture-result')).toHaveTextContent('Vorschlag der KI. Bitte prüfe alle Angaben, besonders Allergene und Übersetzungen.');
    expect(screen.queryByTestId('capture-missing')).not.toBeOnTheScreen();
    expect(screen.getByTestId('new-name-de')).toHaveDisplayValue('Sucuk');
    expect(screen.getByTestId('new-name-ar')).toHaveDisplayValue('سجق تركي');
    expect(screen.getByTestId('new-description-de')).toHaveDisplayValue('Würzige Rinderwurst mit Knoblauch.');
    expect(screen.getByTestId('new-unit')).toHaveDisplayValue('400 g');
    expect(screen.getByTestId('new-price')).toHaveDisplayValue('4,49');
    expect(screen.getByRole('button', { name: 'Fleisch & Geflügel' })).toBeSelected();
    expect(screen.getByTestId('allergen-mustard')).toBeSelected();
    expect(screen.getByTestId('allergen-milk')).not.toBeSelected();
    expect(within(screen.getByTestId('capture-matches')).getByText('Sucuk')).toBeOnTheScreen();

    await user.press(screen.getByTestId('allergen-sesame'));
    await user.press(screen.getByTestId('new-product-submit'));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({
          method: 'POST',
          path: `/merchant/markets/${MARKET_ID}/listings`,
          body: {
            catalogProductId: null,
            newProduct: {
              name: { de: 'Sucuk', en: 'Sucuk beef sausage', ar: 'سجق تركي' },
              description: { de: 'Würzige Rinderwurst mit Knoblauch.' },
              keywords: ['sucuk', 'sujuk'],
              brand: 'Gazi',
              unitSize: '400 g',
              categoryId: 'cat-meat',
              cuisineCodes: ['TURKISH'],
              organic: false,
              allergens: ['mustard', 'sesame'],
              netQuantity: 0.4,
              netUnit: 'kg',
              barcode: '8690000000001',
            },
            price: { amountCents: 449, currency: 'EUR' },
            available: true,
            maxOrderQuantity: 24,
          },
        }),
      ),
    );
  });

  it('takes a catalog match with the price from the note', async () => {
    const { fake, dependencies } = setup(aiDraft);
    const user = await openNewProduct(dependencies);

    await user.type(await screen.findByTestId('capture-text'), 'Sucuk 4,49');
    await user.press(screen.getByTestId('capture-submit'));
    const matches = await screen.findByTestId('capture-matches');
    await user.press(within(matches).getByRole('button', { name: 'Sucuk wählen' }));
    expect(screen.getByTestId(`catalog-price-${sucuk.id}`)).toHaveDisplayValue('4,49');
    await user.press(screen.getByTestId(`catalog-add-${sucuk.id}`));

    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({ method: 'POST', path: `/merchant/markets/${MARKET_ID}/listings`, body: expect.objectContaining({ catalogProductId: sucuk.id, newProduct: null, price: eur(449) }) }),
      ),
    );
  });

  it('asks for input first, then sends the photo key and lists what is still missing', async () => {
    const rulesDraft: ProductDraft = {
      engine: 'RULES',
      product: { name: {}, description: {}, keywords: [], cuisineCodes: [], organic: false, allergens: [], imageUrl: PHOTO_URL },
      price: null,
      catalogMatches: [],
      warnings: ['AI_NOT_CONFIGURED', 'IMAGE_NOT_ANALYSED', 'NAME_MISSING', 'CATEGORY_UNCERTAIN', 'UNIT_SIZE_MISSING', 'PRICE_MISSING'],
    };
    const { fake, dependencies } = setup(rulesDraft);
    const user = await openNewProduct(dependencies);

    await user.press(await screen.findByTestId('capture-submit'));
    expect(await screen.findByText('Schreib etwas oder füge ein Foto hinzu.')).toBeOnTheScreen();
    expect(fake.calls.some((c) => c.path === DRAFT_PATH)).toBe(false);

    await user.press(screen.getByTestId('capture-photo-choose'));
    await screen.findByTestId('capture-photo-remove');
    await user.press(screen.getByTestId('capture-submit'));

    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ path: DRAFT_PATH, body: { imageKey: KEY } })));
    const result = await screen.findByTestId('capture-result');
    expect(result).toHaveTextContent('Aus deinem Text gelesen.', { exact: false });
    expect(result).toHaveTextContent('Das Foto wurde nicht ausgewertet, ist aber als Produktfoto übernommen.', { exact: false });
    expect(screen.getByTestId('capture-missing')).toHaveTextContent('Bitte noch ergänzen: Name, Kategorie, Packungsgröße, Preis (€)');
    expect(screen.queryByTestId('capture-matches')).not.toBeOnTheScreen();
    expect(screen.getByTestId('new-photo-remove')).toBeOnTheScreen();
  });

  it('derives the media key from an upload URL', () => {
    expect(mediaKeyOf(PHOTO_URL)).toBe(KEY);
    expect(mediaKeyOf('https://cdn.example/x.png')).toBeNull();
    expect(mediaKeyOf(null)).toBeNull();
  });
});
