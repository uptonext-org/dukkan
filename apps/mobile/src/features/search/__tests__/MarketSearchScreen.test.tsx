import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, MARKET_ID, marketHome, page, product } from '../../../test/fixtures';
import { createTestDependencies, normalize, renderApp } from '../../../test/render';

describe('Market sub-app: Suchen', () => {
  it('shows quick links and categories, suggests while typing and shows a 2-column result grid with sort', async () => {
    const fake = createFakeApiClient({
      'GET /market-types': [],
      'GET /cuisines': [],
      'GET /markets': page([market()]),
      [`GET /markets/${MARKET_ID}`]: market(),
      [`GET /markets/${MARKET_ID}/home`]: marketHome(),
      [`GET /markets/${MARKET_ID}/categories`]: [
        { id: 'c-obst', parentId: null, slug: 'obst', name: 'Obst', sortOrder: 2, productCount: 12 },
        { id: 'c-reg', parentId: null, slug: 'regional', name: 'Regional', sortOrder: 1, productCount: 4 },
        { id: 'c-sub', parentId: 'c-obst', slug: 'aepfel', name: 'Äpfel', sortOrder: 1 },
      ],
      [`GET /markets/${MARKET_ID}/search/suggestions`]: ['Äpfel Pink Lady', 'Apfelsaft'],
      [`GET /markets/${MARKET_ID}/products`]: page([product(), product({ id: 'p2', name: 'Apfelsaft', organic: false })]),
    });
    await renderApp(createTestDependencies(fake.client));
    const user = userEvent.setup();
    await user.press(await screen.findByTestId(`market-card-${MARKET_ID}`));
    await user.press(await screen.findByRole('button', { name: 'Suchen, Tab 3 von 4' }));

    expect(await screen.findByTestId('quick-offers')).toHaveAccessibleName('Alle Angebote');
    expect(screen.getByTestId('quick-new')).toHaveAccessibleName('Neu im Markt');
    expect(screen.getByTestId('quick-organic')).toHaveAccessibleName('Bio');
    expect(screen.getByRole('header', { name: 'Alle Kategorien' })).toBeOnTheScreen();
    // Top-level categories in sortOrder.
    const categories = await screen.findAllByTestId(/^category-/);
    expect(categories.map((c) => c.props.testID as string)).toEqual(['category-c-reg', 'category-c-obst']);

    await user.type(screen.getByTestId('market-search-field'), 'apf');
    await user.press(await screen.findByRole('button', { name: 'Apfelsaft' }));
    const results = await screen.findByTestId('market-search-results');
    expect(within(results).getByText('2 Ergebnisse', { normalizer: normalize })).toBeOnTheScreen();
    expect(fake.calls.filter((c) => c.path.endsWith('/products')).at(-1)?.query).toMatchObject({ q: 'Apfelsaft', sort: 'relevance' });

    await user.press(screen.getByRole('button', { name: 'Preis aufsteigend' }));
    await waitFor(() => expect(fake.calls.filter((c) => c.path.endsWith('/products')).at(-1)?.query).toMatchObject({ sort: 'price_asc' }));
    await user.press(screen.getByRole('button', { name: 'Nur Angebote' }));
    await waitFor(() => expect(fake.calls.filter((c) => c.path.endsWith('/products')).at(-1)?.query).toMatchObject({ promotedOnly: true }));
  });
});
