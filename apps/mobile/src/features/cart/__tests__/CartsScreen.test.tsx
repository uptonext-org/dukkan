import { screen, userEvent, within } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart, eur, market, MARKET_ID, marketHome, page } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';

describe('Warenkörbe tab', () => {
  it('lists one row per market cart, badges the open-cart count and opens that market\'s cart', async () => {
    const second = market({ id: 'm2', name: 'Bäckerei Kaya' });
    const fake = createFakeApiClient({
      'GET /market-types': [],
      'GET /cuisines': [],
      'GET /markets': page([market()]),
      'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
      'GET /carts': [
        { cartId: 'c1', market: market(), itemCount: 2, total: eur(1079), updatedAt: '2026-10-20T18:00:00+02:00' },
        { cartId: 'c2', market: second, itemCount: 1, total: eur(250), updatedAt: '2026-10-20T17:00:00+02:00' },
      ],
      'GET /favorites/markets': [],
      [`GET /markets/${MARKET_ID}`]: market(),
      [`GET /markets/${MARKET_ID}/home`]: marketHome(),
      [`GET /markets/${MARKET_ID}/cart`]: cart(),
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = userEvent.setup();

    const tab = await screen.findByLabelText('Warenkörbe, 2 offene Warenkörbe');
    expect(within(tab).getByText('2')).toBeOnTheScreen();
    await user.press(tab);

    expect(await screen.findByRole('button', { name: 'Anatolia Markt, 2 Artikel, 10,79 Euro' })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: 'Bäckerei Kaya, 1 Artikel, 2,50 Euro' })).toBeOnTheScreen();

    await user.press(screen.getByTestId(`cart-summary-${MARKET_ID}`));
    expect(await screen.findByTestId('market-cart')).toBeOnTheScreen();

    // Before paying: the market's name at the top leads to its start page.
    const name = screen.getByTestId('market-header-name');
    expect(name).toHaveAccessibleName('Anatolia Markt');
    await user.press(name);
    expect(await screen.findByTestId('market-home')).toBeOnTheScreen();
    expect(screen.queryByTestId('market-cart')).not.toBeOnTheScreen();
  });
});
