import { fireEvent, screen, userEvent, waitFor, within } from '@testing-library/react-native';

import type { ApiRequest } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { market, MARKET_ID, marketHome, order, page, product } from '../../../test/fixtures';
import { createTestDependencies, normalize, renderApp, ST_GEORG } from '../../../test/render';
import { mergeMyMarkets } from '../components/MyMarkets';
import { buildMarketQuery, DEFAULT_FILTERS } from '../model';
import { addVisit } from '../visits/VisitedMarkets';

const types = [
  { code: 'SUPERMARKET', name: 'Supermarkt' },
  { code: 'BAKERY', name: 'Bäckerei' },
  { code: 'BUTCHER', name: 'Metzgerei' },
];
const cuisines = [
  { code: 'TURKISH', name: 'Türkisch' },
  { code: 'ARABIC', name: 'Arabisch' },
];

function setup() {
  const fake = createFakeApiClient({
    'GET /market-types': types,
    'GET /cuisines': cuisines,
    'GET /markets': page([
      market(),
      market({
        id: 'm2',
        name: 'Bäckerei Kaya',
        openNow: false,
        todayHours: null,
        nextOpeningAt: '2026-10-21T07:00:00+02:00',
        activePromotionCount: 0,
        fulfilment: { delivery: false, pickup: true, minimumOrderValue: { amountCents: 0, currency: 'EUR' } },
      }),
    ]),
    [`GET /markets/${MARKET_ID}`]: market(),
    [`GET /markets/${MARKET_ID}/home`]: marketHome(),
  });
  return { fake, dependencies: createTestDependencies(fake.client) };
}

const marketCalls = (calls: ApiRequest[]) => calls.filter((c) => c.path === '/markets');

describe('buildMarketQuery', () => {
  const at = ST_GEORG.location;

  it('sends only the location and default sort without filters', () => {
    expect(buildMarketQuery(DEFAULT_FILTERS, at)).toEqual({
      lat: at.lat,
      lng: at.lng,
      sort: 'distance',
      size: 20,
    });
  });

  it('maps chips and sheet filters to the contract parameters', () => {
    expect(
      buildMarketQuery(
        {
          ...DEFAULT_FILTERS,
          types: ['BUTCHER', 'BAKERY'],
          cuisines: ['TURKISH'],
          delivery: true,
          openNow: true,
          hasOffers: true,
          sort: 'delivery_fee',
        },
        at,
      ),
    ).toEqual({
      lat: at.lat,
      lng: at.lng,
      sort: 'delivery_fee',
      size: 20,
      types: ['BAKERY', 'BUTCHER'],
      cuisines: ['TURKISH'],
      fulfilment: 'DELIVERY',
      openNow: true,
      hasOffers: true,
    });
  });

  it('treats delivery + pickup as "any" because the contract takes one fulfilment value', () => {
    expect(buildMarketQuery({ ...DEFAULT_FILTERS, delivery: true, pickup: true }, at)).not.toHaveProperty(
      'fulfilment',
    );
    expect(buildMarketQuery({ ...DEFAULT_FILTERS, pickup: true }, at)).toMatchObject({
      fulfilment: 'PICKUP',
    });
  });
});

describe('<DiscoverScreen />', () => {
  it('shows market cards per the design and builds the query from chips and the filter sheet', async () => {
    const { fake, dependencies } = setup();
    await renderApp(dependencies);
    const user = userEvent.setup();

    const card = await screen.findByTestId(`market-card-${MARKET_ID}`);
    expect(within(card).getByText('Anatolia Markt')).toBeOnTheScreen();
    expect(within(card).getByText('Lebensmittelladen · Türkisch')).toBeOnTheScreen();
    expect(
      within(card).getByText('1,2 km · Geöffnet bis 20:00', { normalizer: normalize }),
    ).toBeOnTheScreen();
    expect(
      within(card).getByText('Mindestbestellwert 15 € · Lieferung 2,99 €', { normalizer: normalize }),
    ).toBeOnTheScreen();
    expect(within(card).getByText('3 Angebote', { normalizer: normalize })).toBeOnTheScreen();
    expect(
      screen.getByText('Geschlossen · öffnet 07:00', { exact: false, normalizer: normalize }),
    ).toBeOnTheScreen();
    expect(screen.getByTestId('location-button')).toHaveTextContent('St. Georg');
    expect(marketCalls(fake.calls)[0]?.query).toEqual({
      lat: ST_GEORG.location.lat,
      lng: ST_GEORG.location.lng,
      sort: 'distance',
      size: 20,
      page: 0,
    });

    // Type chip.
    await user.press(screen.getByRole('button', { name: 'Bäckerei' }));
    await waitFor(() => expect(marketCalls(fake.calls).at(-1)?.query).toMatchObject({ types: ['BAKERY'] }));
    expect(screen.getByRole('button', { name: 'Bäckerei' })).toBeSelected();
    expect(screen.getByRole('button', { name: 'Alle' })).not.toBeSelected();

    // Cuisine chip.
    await user.press(screen.getByRole('button', { name: 'Arabisch' }));
    await waitFor(() =>
      expect(marketCalls(fake.calls).at(-1)?.query).toMatchObject({
        types: ['BAKERY'],
        cuisines: ['ARABIC'],
      }),
    );

    // Sort and filter sheet.
    await user.press(screen.getByTestId('filter-button'));
    const sheet = await screen.findByTestId('filter-sheet');
    await user.press(within(sheet).getByTestId('sort-delivery_fee'));
    await fireEvent(within(sheet).getByTestId('filter-delivery'), 'valueChange', true);
    await fireEvent(within(sheet).getByTestId('filter-openNow'), 'valueChange', true);
    await user.press(within(sheet).getByTestId('filter-apply'));

    await waitFor(() =>
      expect(marketCalls(fake.calls).at(-1)?.query).toEqual({
        lat: ST_GEORG.location.lat,
        lng: ST_GEORG.location.lng,
        sort: 'delivery_fee',
        size: 20,
        page: 0,
        types: ['BAKERY'],
        cuisines: ['ARABIC'],
        fulfilment: 'DELIVERY',
        openNow: true,
      }),
    );
    expect(screen.getByTestId('filter-button')).toHaveAccessibleName('Sortieren und filtern, 3 aktiv');
  });

  it('switches to the map and shows a bottom card for the selected market', async () => {
    const { dependencies } = setup();
    await renderApp(dependencies);
    const user = userEvent.setup();
    await screen.findByTestId(`market-card-${MARKET_ID}`);

    await user.press(screen.getByRole('tab', { name: 'Karte' }));
    expect(await screen.findByTestId('market-map')).toBeOnTheScreen();
    expect(screen.queryByTestId('selected-market-card')).not.toBeOnTheScreen();
    await user.press(screen.getByTestId(`marker-${MARKET_ID}`));
    expect(await screen.findByTestId('selected-market-card')).toHaveTextContent(/Anatolia Markt/);

    // Opening the market starts its sub-app.
    await user.press(within(screen.getByTestId('selected-market-card')).getByRole('button'));
    expect(await screen.findByTestId('market-home')).toBeOnTheScreen();
  });

  it('shows "Meine Märkte": bought-from markets with what was bought, and remembers visited markets', async () => {
    const bakery = market({ id: 'm2', name: 'Bäckerei Kaya' });
    const past = order({ id: 'o-last', status: 'COMPLETED' });
    const fake = createFakeApiClient({
      'GET /market-types': types,
      'GET /cuisines': cuisines,
      'GET /markets': page([market(), bakery]),
      'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
      'GET /me/notifications': { ...page([]), unreadCount: 0 },
      'GET /carts': [],
      'GET /favorites/markets': [],
      [`GET /markets/${MARKET_ID}`]: market(),
      [`GET /markets/${MARKET_ID}/home`]: marketHome(),
      'GET /markets/m2': bakery,
      'GET /markets/m2/home': { ...marketHome(), market: bakery },
      'GET /markets/recent': (request: ApiRequest) => {
        const visited = (request.query?.visited as string[] | undefined) ?? [];
        return [
          {
            market: market(),
            lastOrderAt: '2026-10-18T10:00:00+02:00',
            lastOrderId: past.id,
            orderCount: 3,
            recentProducts: [product()],
          },
          ...(visited.includes('m2')
            ? [{ market: bakery, lastOrderAt: null, lastOrderId: null, orderCount: 0, recentProducts: [] }]
            : []),
        ];
      },
      [`GET /orders/${past.id}`]: past,
    });
    await renderApp(createTestDependencies(fake.client, { token: 'jwt' }));
    const user = userEvent.setup();

    // Visit the bakery first, then come back.
    await user.press(await screen.findByTestId('market-card-m2'));
    await screen.findByTestId('market-home');
    await user.press(screen.getByTestId('market-exit'));

    await user.press(await screen.findByRole('tab', { name: 'Meine' }));
    const mine = await screen.findByTestId(`my-market-${MARKET_ID}`);
    expect(within(mine).getByText(/Zuletzt bestellt am .* · .?3.? Bestellungen/)).toBeOnTheScreen();
    expect(within(mine).getByText('Hier gekauft')).toBeOnTheScreen();
    // The visited market appears below, and was sent to the server.
    expect(await screen.findByTestId('my-market-m2')).toHaveTextContent(/Zuletzt angesehen am/);
    expect(
      fake.calls.some(
        (c) => c.path === '/markets/recent' && (c.query?.visited as string[] | undefined)?.includes('m2'),
      ),
    ).toBe(true);
    // Chips and filters are for the list and map only.
    expect(screen.queryByTestId('type-chips')).not.toBeOnTheScreen();

    await user.press(within(mine).getByTestId(`my-market-order-${MARKET_ID}`));
    expect(await screen.findByText(past.orderNumber, { exact: false })).toBeOnTheScreen();
  });

  it('merges orders and visits by the latest activity and keeps each market once', () => {
    const recent = [
      {
        market: market(),
        lastOrderAt: '2026-10-10T10:00:00Z',
        lastOrderId: 'o1',
        orderCount: 1,
        recentProducts: [],
      },
      {
        market: market({ id: 'm2' }),
        lastOrderAt: null,
        lastOrderId: null,
        orderCount: 0,
        recentProducts: [],
      },
    ];
    const visits = addVisit(addVisit([], MARKET_ID, '2026-10-01T10:00:00Z'), 'm2', '2026-10-15T10:00:00Z');
    expect(mergeMyMarkets(recent, visits).map((m) => m.market.id)).toEqual(['m2', MARKET_ID]);
    expect(addVisit(visits, MARKET_ID, '2026-10-20T10:00:00Z').map((v) => v.marketId)).toEqual([
      MARKET_ID,
      'm2',
    ]);
  });
});
