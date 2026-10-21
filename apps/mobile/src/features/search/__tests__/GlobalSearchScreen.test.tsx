import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { eur, market, MARKET_ID, marketHome, page, product } from '../../../test/fixtures';
import { createTestDependencies, ST_GEORG, renderApp } from '../../../test/render';
import { groupOffers } from '../screens/GlobalSearchScreen';

const kaya = market({ id: 'm2', name: 'Bäckerei Kaya', distanceMeters: 640 });
const apples = product({ promotionalPrice: null, promotion: null });
const offers = [
  { product: apples, market: market() },
  { product: { ...apples, id: 'p-kaya', marketId: 'm2', price: eur(299), promotionalPrice: eur(249) }, market: kaya },
  { product: product({ id: 'p-other', catalogProductId: 'cat-2', name: 'Apfelsaft', organic: false, promotion: null, promotionalPrice: null, price: eur(189) }), market: kaya },
];

describe('groupOffers', () => {
  it('groups by catalog product and keeps server order', () => {
    const groups = groupOffers(offers);
    expect(groups.map((g) => [g.name, g.offers.map((o) => o.market.name)])).toEqual([
      ['Äpfel Pink Lady', ['Anatolia Markt', 'Bäckerei Kaya']],
      ['Apfelsaft', ['Bäckerei Kaya']],
    ]);
  });
});

describe('Suchen (all markets)', () => {
  it('shows products with market, price and distance, sorts, and opens the product inside its market', async () => {
    const fake = createFakeApiClient({
      'GET /market-types': [],
      'GET /cuisines': [],
      'GET /markets': page([market()]),
      'GET /search/products': page(offers),
      [`GET /markets/${MARKET_ID}`]: market(),
      [`GET /markets/${MARKET_ID}/home`]: marketHome(),
      [`GET /products/${apples.id}`]: { ...apples, categoryId: 'c1', allergens: [], cuisines: [], market: market(), description: 'Knackig' },
    });
    await renderApp(createTestDependencies(fake.client));
    const user = userEvent.setup();
    await user.press(await screen.findByRole('button', { name: 'Suchen, Tab 2 von 4' }));

    expect(await screen.findByText('Preise vergleichen')).toBeOnTheScreen();
    await user.type(screen.getByTestId('global-search-field'), 'apfel');

    const results = await screen.findByTestId('global-search-results');
    expect(within(results).getByRole('header', { name: 'Äpfel Pink Lady' })).toBeOnTheScreen();
    expect(within(results).getByRole('button', { name: 'Äpfel Pink Lady, 550 g, Anatolia Markt, 1,2 km, 3,49 Euro' })).toBeOnTheScreen();
    expect(within(results).getByRole('button', { name: 'Äpfel Pink Lady, 550 g, Bäckerei Kaya, 640 m, 2,49 Euro' })).toBeOnTheScreen();
    expect(within(results).getByRole('button', { name: 'Apfelsaft, 550 g, Bäckerei Kaya, 640 m, 1,89 Euro' })).toBeOnTheScreen();
    const search = fake.calls.filter((c) => c.path === '/search/products').at(-1);
    expect(search?.query).toMatchObject({ q: 'apfel', lat: ST_GEORG.location.lat, lng: ST_GEORG.location.lng, sort: 'relevance', page: 0 });

    await user.press(screen.getByTestId('offer-sort-price_asc'));
    await waitFor(() => expect(fake.calls.filter((c) => c.path === '/search/products').at(-1)?.query).toMatchObject({ sort: 'price_asc' }));

    await user.press(within(screen.getByTestId('global-search-results')).getByTestId(`offer-${apples.id}`));
    expect(await screen.findByTestId('product-detail')).toHaveTextContent(/Knackig/);
    // The product opens inside the market sub-app: its own back goes to the market tabs.
    await user.press(screen.getByRole('button', { name: 'Zurück' }));
    expect(await screen.findByTestId('market-home')).toBeOnTheScreen();
  });
});
