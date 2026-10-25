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
  it('sends feedback to the market when it takes feedback; guests sign in first', async () => {
    const { fake, dependencies } = setup('jwt', {
      'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
      [`GET /markets/${MARKET_ID}/feedback`]: { enabled: true, maxLength: 1000 },
      [`POST /markets/${MARKET_ID}/feedback`]: () => undefined,
    });
    await renderApp(dependencies);
    const user = await openMarket();

    const card = await screen.findByTestId('market-feedback');
    expect(within(card).getByText('Feedback an den Markt')).toBeOnTheScreen();
    await user.press(within(card).getByTestId('feedback-send'));
    expect(await within(card).findByText('Bitte eine Nachricht eingeben.')).toBeOnTheScreen();
    expect(within(card).getByLabelText(/4\W* von 5 Sternen/)).toBeOnTheScreen();
    await user.press(within(card).getByTestId('feedback-star-4'));
    await user.type(within(card).getByTestId('feedback-message'), 'Bitte mehr Bio-Joghurt!');
    await user.press(within(card).getByTestId('feedback-send'));
    expect(await screen.findByTestId('feedback-sent')).toHaveTextContent('Danke! Der Markt hat deine Nachricht erhalten.');
    expect(fake.calls).toContainEqual(
      expect.objectContaining({ method: 'POST', path: `/markets/${MARKET_ID}/feedback`, body: { message: 'Bitte mehr Bio-Joghurt!', rating: 4 } }),
    );
  });

  it('shows no feedback field while the market takes none, and a sign-in button for guests', async () => {
    const { dependencies } = setup(null, { [`GET /markets/${MARKET_ID}/feedback`]: { enabled: true, maxLength: 1000 } });
    await renderApp(dependencies);
    await openMarket();
    expect(await screen.findByTestId('feedback-sign-in')).toBeOnTheScreen();
    expect(screen.queryByTestId('feedback-message')).toBeNull();
  });

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

  it('puts a favourite heart on every product of the start page; it changes at once', async () => {
    let favorite = false;
    const home = marketHome();
    const { fake, dependencies } = setup('jwt', {
      'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      [`GET /markets/${MARKET_ID}/home`]: () => ({
        ...home,
        sections: home.sections.map((section) => ({ ...section, items: section.items.map((item) => ({ ...item, favorite })) })),
      }),
      [`GET /markets/${MARKET_ID}/cart`]: cart({ items: [] }),
      [`PUT /favorites/products/${PRODUCT_ID}`]: () => {
        favorite = true;
        return undefined;
      },
    });
    await renderApp(dependencies);
    const user = await openMarket();

    const firstTile = () => {
      const [tile] = screen.getAllByTestId(`product-tile-${PRODUCT_ID}`);
      if (tile === undefined) {
        throw new Error('no tile');
      }
      return tile;
    };
    const name = product().name;
    await user.press(within(firstTile()).getByRole('button', { name: `${name} zu Favoriten` }));
    // Optimistic: the heart is filled before the server answers and the page reloads.
    expect(await within(firstTile()).findByRole('button', { name: `${name} aus Favoriten entfernen` })).toBeOnTheScreen();
    await waitFor(() => expect(fake.calls.some((c) => c.method === 'PUT' && c.path === `/favorites/products/${PRODUCT_ID}`)).toBe(true));
  });

  it('puts the heart back when the server refuses the favourite', async () => {
    let loads = 0;
    const { dependencies } = setup('jwt', {
      // The refresh after the error never answers (bad network): only the rollback restores the heart.
      [`GET /markets/${MARKET_ID}/home`]: () => {
        loads += 1;
        return loads > 1 ? new Promise(() => undefined) : marketHome();
      },
      'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      [`GET /markets/${MARKET_ID}/cart`]: cart({ items: [] }),
      [`PUT /favorites/products/${PRODUCT_ID}`]: () => {
        throw new ApiError({ kind: 'http', status: 500, code: 'INTERNAL_ERROR', detail: 'boom' });
      },
    });
    await renderApp(dependencies);
    const user = await openMarket();

    const name = product().name;
    const [tile] = screen.getAllByTestId(`product-tile-${PRODUCT_ID}`);
    if (tile === undefined) {
      throw new Error('no tile');
    }
    await user.press(within(tile).getByRole('button', { name: `${name} zu Favoriten` }));
    await waitFor(() => expect(within(screen.getAllByTestId(`product-tile-${PRODUCT_ID}`)[0] ?? tile).getByRole('button', { name: `${name} zu Favoriten` })).toBeOnTheScreen());
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

describe('Market sub-app: what the merchant switched on', () => {
  it('shows a market without online orders as a showcase: products, but no "+", cart tab, delivery choice or notice', async () => {
    const showcase = market({ acceptsOrders: false });
    const { dependencies } = setup(null, {
      [`GET /markets/${MARKET_ID}`]: showcase,
      [`GET /markets/${MARKET_ID}/home`]: marketHome({ market: showcase }),
    });
    await renderApp(dependencies);
    await openMarket();

    const tile = screen.getByTestId(`product-tile-${PRODUCT_ID}`);
    expect(within(tile).queryByRole('button', { name: /in den Warenkorb/ })).not.toBeOnTheScreen();
    expect(screen.queryByTestId('market-tab-cart')).not.toBeOnTheScreen();
    expect(screen.queryByRole('button', { name: 'Lieferzeit wählen' })).not.toBeOnTheScreen();
    expect(screen.queryByTestId('ordering-notice')).not.toBeOnTheScreen();
  });

  it('announces a pause and a holiday above the market', async () => {
    const paused = market({ acceptsOrders: false, ordersPausedUntil: '2026-11-03T06:00:00Z', closedFrom: '2026-12-24', closedUntil: '2026-12-26' });
    const { dependencies } = setup(null, {
      'GET /platform': { legal: {}, supportEmail: null, payment: 'ON_SITE' },
      [`GET /markets/${MARKET_ID}`]: { ...paused, paymentMethods: ['CASH', 'CARD'] },
      [`GET /markets/${MARKET_ID}/home`]: marketHome({ market: paused }),
    });
    await renderApp(dependencies);
    await openMarket();

    const notice = screen.getByTestId('ordering-notice');
    expect(within(notice).getByText(/Bestellungen wieder ab 03\.11\.2026, 07:00/, { normalizer: normalize })).toBeOnTheScreen();
    expect(within(notice).getByText(/Geschlossen vom 24\.12\.2026 bis 26\.12\.2026/, { normalizer: normalize })).toBeOnTheScreen();

  });

  it('shows how to pay at handover when payment is on site', async () => {
    const { dependencies } = setup(null, {
      'GET /platform': { legal: {}, supportEmail: null, payment: 'ON_SITE' },
      [`GET /markets/${MARKET_ID}`]: { ...market(), paymentMethods: ['CASH', 'CARD'] },
    });
    await renderApp(dependencies);
    await openMarket();
    const info = screen.getByTestId('market-info-card');
    expect(await within(info).findByText('Bezahlung bei Übergabe: bar oder mit Karte', { normalizer: normalize })).toBeOnTheScreen();
  });
});
