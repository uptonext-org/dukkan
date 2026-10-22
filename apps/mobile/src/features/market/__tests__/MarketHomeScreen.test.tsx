import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import { ApiError } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart, cartItem, eur, market, MARKET_ID, marketHome, page, product } from '../../../test/fixtures';
import { createTestDependencies, normalize, renderApp } from '../../../test/render';

const PRODUCT_ID = product().id;

function setup(token: string | null, extra: Parameters<typeof createFakeApiClient>[0] = {}) {
  const fake = createFakeApiClient({
    'GET /market-types': [],
    'GET /cuisines': [],
    'GET /markets': page([market()]),
    [`GET /markets/${MARKET_ID}`]: market(),
    [`GET /markets/${MARKET_ID}/home`]: marketHome(),
    'GET /favorites/markets': [],
    'GET /carts': [],
    ...extra,
  });
  return { fake, dependencies: createTestDependencies(fake.client, { token }) };
}

async function openMarket() {
  const user = userEvent.setup();
  await user.press(await screen.findByTestId(`market-card-${MARKET_ID}`));
  await screen.findByTestId('market-home');
  return user;
}

describe('Market sub-app: Entdecken', () => {
  it('renders the header, fulfilment link, banners with peek, info card and product sections', async () => {
    const { dependencies } = setup(null);
    await renderApp(dependencies);
    await openMarket();

    // Header: back to the marketplace, logo + name, favourite heart.
    expect(screen.getByRole('button', { name: 'Zurück zum Marktplatz' })).toBeOnTheScreen();
    expect(screen.getByRole('header', { name: 'Anatolia Markt' })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: 'Anatolia Markt merken' })).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: 'Lieferzeit wählen' })).toBeOnTheScreen();

    // Banners (no image → tint placeholder) in a horizontal carousel.
    const carousel = screen.getByTestId('banner-carousel');
    expect(within(carousel).getByRole('button', { name: 'Saftig und frisch, Obst und Gemüse der Saison' })).toBeOnTheScreen();
    expect(within(carousel).getByRole('button', { name: 'Neu im Regal' })).toBeOnTheScreen();

    // Info card on surfaceRose.
    const info = screen.getByTestId('market-info-card');
    expect(within(info).getByText('Geöffnet bis 20:00 · heute 08:00–20:00', { normalizer: normalize })).toBeOnTheScreen();
    // The fee belongs to delivery: "Lieferung (2,99 €) · Abholung", not a third option.
    expect(within(info).getByText('Lieferung (2,99 €) · Abholung', { normalizer: normalize })).toBeOnTheScreen();

    // Sections with items; empty sections are skipped.
    expect(screen.getByTestId('home-section-PROMOTIONS')).toHaveTextContent(/Angebote der Woche/);
    expect(screen.getByTestId('home-section-NEW')).toHaveTextContent(/Neu im Markt.*Frisch eingetroffen/);
    expect(screen.queryByTestId('home-section-FREQUENTLY_ORDERED')).not.toBeOnTheScreen();
    expect(screen.getByTestId(`product-tile-${PRODUCT_ID}`)).toBeOnTheScreen();
  });

  it('shows the best sellers and lists all of them in their rank order', async () => {
    const home = marketHome();
    const { fake, dependencies } = setup(null, {
      [`GET /markets/${MARKET_ID}/home`]: {
        ...home,
        sections: [{ key: 'BESTSELLERS', title: 'Meistverkauft', subtitle: 'Das kaufen die anderen hier gerade', items: [product()] }, ...home.sections],
      },
      [`GET /markets/${MARKET_ID}/products`]: () => page([product()]),
    });
    await renderApp(dependencies);
    const user = await openMarket();

    const section = screen.getByTestId('home-section-BESTSELLERS');
    expect(section).toHaveTextContent(/Meistverkauft.*Das kaufen die anderen hier gerade/);
    await user.press(within(section).getByRole('button', { name: 'Alle anzeigen: Meistverkauft' }));
    await waitFor(() => expect(fake.calls.some((c) => c.path === `/markets/${MARKET_ID}/products` && c.query?.bestsellersOnly === true)).toBe(true));
    // No sort chips: the order is the rank.
    expect(screen.queryByRole('button', { name: 'Preis aufsteigend' })).not.toBeOnTheScreen();
  });

  it('shows a band of the market\'s categories right below the delivery time; a tap opens the category', async () => {
    const category = (id: string, slug: string, name: string, sortOrder: number, extra: object = {}) => ({ id, slug, name, sortOrder, parentId: null, iconKey: slug, productCount: 4, ...extra });
    const { fake, dependencies } = setup(null, {
      [`GET /markets/${MARKET_ID}/categories`]: [
        category('c-meat', 'meat', 'Fleisch & Geflügel', 30),
        category('c-veg', 'fruit-veg', 'Obst & Gemüse', 10),
        category('c-apples', 'apples', 'Äpfel', 11, { parentId: 'c-veg' }),
        category('c-empty', 'frozen', 'Tiefkühl', 90, { productCount: 0 }),
      ],
      [`GET /markets/${MARKET_ID}/products`]: () => page([product()]),
    });
    await renderApp(dependencies);
    const user = await openMarket();

    const band = await screen.findByTestId('category-band');
    // Top-level categories with products only, in the market's order.
    const chips = within(band).getAllByRole('button').map((chip) => chip.props.accessibilityLabel as string);
    expect(chips).toEqual(['Obst & Gemüse', 'Fleisch & Geflügel']);

    await user.press(within(band).getByRole('button', { name: 'Fleisch & Geflügel' }));
    await waitFor(() => expect(fake.calls.some((c) => c.path === `/markets/${MARKET_ID}/products` && c.query?.categoryId === 'c-meat')).toBe(true));
  });

  it('asks a guest to sign in when adding to the cart, then continues with the add', async () => {
    const afterAdd = cart({ items: [cartItem({ productId: PRODUCT_ID, name: 'Äpfel Pink Lady', quantity: 1 })], itemCount: 1, total: eur(279) });
    const { fake, dependencies } = setup(null, {
      'POST /auth/login': {
        accessToken: 'jwt-1',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
      },
      [`GET /markets/${MARKET_ID}/cart`]: cart({ items: [], itemCount: 0, total: eur(0) }),
      [`PUT /markets/${MARKET_ID}/cart/items/${PRODUCT_ID}`]: afterAdd,
    });
    await renderApp(dependencies);
    const user = await openMarket();

    const [add] = screen.getAllByRole('button', { name: 'Äpfel Pink Lady in den Warenkorb' });
    if (add === undefined) {
      throw new Error('add button missing');
    }
    await user.press(add);

    const sheet = await screen.findByTestId('auth-sheet');
    expect(within(sheet).getByText(/Melde dich an, um Produkte in den Warenkorb zu legen/)).toBeOnTheScreen();
    expect(fake.calls.some((c) => c.method === 'PUT')).toBe(false);

    await user.type(within(sheet).getByLabelText('E-Mail'), 'ada@example.de');
    await user.type(within(sheet).getByLabelText('Passwort'), 'secret-password');
    await user.press(within(sheet).getByTestId('auth-submit'));

    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({ method: 'PUT', path: `/markets/${MARKET_ID}/cart/items/${PRODUCT_ID}`, body: { quantity: 1 } }),
      ),
    );
    expect(screen.queryByTestId('auth-sheet')).not.toBeOnTheScreen();
    // The cart tab badge shows the returned total.
    expect(await screen.findByLabelText('Warenkorb, 2,79 Euro')).toBeOnTheScreen();
  });

  it('opens the sign-in sheet when the backend requires login to browse (401 on a public endpoint)', async () => {
    const { dependencies } = setup(null, {
      'GET /markets': () => {
        throw new ApiError({ kind: 'http', status: 401, code: 'UNAUTHORIZED' });
      },
    });
    await renderApp(dependencies);
    const sheet = await screen.findByTestId('auth-sheet');
    expect(within(sheet).getByText('Zum Stöbern ist gerade ein Konto nötig.')).toBeOnTheScreen();
  });
});
