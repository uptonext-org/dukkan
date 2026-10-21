import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart, eur, market, MARKET_ID, marketHome, page, product } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

const base = {
  'GET /market-types': [],
  'GET /cuisines': [],
  'GET /markets': page([market()]),
  [`GET /markets/${MARKET_ID}`]: market(),
  [`GET /markets/${MARKET_ID}/home`]: marketHome(),
};

async function openFavorites() {
  const user = userEvent.setup();
  await user.press(await screen.findByTestId(`market-card-${MARKET_ID}`));
  await user.press(await screen.findByRole('button', { name: 'Favoriten, Tab 2 von 4' }));
  await screen.findByTestId('market-favorites');
  return user;
}

describe('Market sub-app: Favoriten', () => {
  it('shows the title, explanation and illustration, and a sign-in prompt for guests', async () => {
    await renderApp(createTestDependencies(createFakeApiClient(base).client));
    await openFavorites();
    expect(screen.getByRole('header', { name: 'Hier kommen deine Favoriten' })).toBeOnTheScreen();
    expect(screen.getByText(/Mit dem Herz kannst du auch selbst Produkte merken/)).toBeOnTheScreen();
    expect(screen.getByTestId('favorites-illustration', { includeHiddenElements: true })).toBeOnTheScreen();
    expect(screen.getByText('Melde dich an, um Favoriten zu sehen und zu speichern.')).toBeOnTheScreen();
  });

  it('groups favourites by category and toggles the heart', async () => {
    const apples = product({ favorite: true });
    const fake = createFakeApiClient({
      ...base,
      'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'A', lastName: 'B', roles: ['CUSTOMER'] },
      'GET /carts': [],
      'GET /favorites/markets': [],
      [`GET /markets/${MARKET_ID}/cart`]: cart({ items: [], itemCount: 0, total: eur(0) }),
      [`GET /markets/${MARKET_ID}/favorites`]: [
        { category: { id: 'c-obst', slug: 'obst', name: 'Obst', sortOrder: 1 }, items: [{ ...apples, favoriteReason: 'MANUAL' }] },
        { category: { id: 'c-milch', slug: 'milch', name: 'Milchprodukte', sortOrder: 2 }, items: [{ ...product({ id: 'p-ayran', name: 'Ayran', organic: false }), favoriteReason: 'FREQUENTLY_ORDERED' }] },
      ],
      [`DELETE /favorites/products/${apples.id}`]: () => undefined,
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = await openFavorites();
    expect(await screen.findByRole('header', { name: 'Obst' })).toBeOnTheScreen();
    expect(screen.getByRole('header', { name: 'Milchprodukte' })).toBeOnTheScreen();
    expect(screen.getByText('Oft bestellt')).toBeOnTheScreen();
    const tile = screen.getByTestId(`product-tile-${apples.id}`);
    await user.press(within(tile).getByRole('button', { name: 'Äpfel Pink Lady aus Favoriten entfernen' }));
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'DELETE', path: `/favorites/products/${apples.id}` })));
  });
});
