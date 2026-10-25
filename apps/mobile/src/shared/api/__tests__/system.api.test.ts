/**
 * System test: a whole market life cycle against a running backend (black box, real HTTP, real
 * database, outbox and notifications), driven through the app's own API layer
 * (`createFetchApiClient` + `createDukkanApi`), plus the portal's admin and insights endpoints.
 *
 * Everything is created fresh per run (new merchant, market far away in Munich, new customer), so
 * the test neither depends on nor disturbs the Hamburg seed data, and caches of other markets
 * (best sellers) do not interfere.
 *
 * Skipped unless DUKKAN_LIVE_API_URL is set, e.g.:
 *   DUKKAN_LIVE_API_URL=http://localhost:8080/api/v1 npx jest system.api
 */
import { ApiError } from '../ApiError';
import { createFetchApiClient, type ApiClient } from '../client';
import { createDukkanApi, type DukkanApi } from '../dukkanApi';
import type { Cart, MarketProfileRequest, Order } from '../types';

declare const process: { env: Record<string, string | undefined> };

const baseUrl = process.env.DUKKAN_LIVE_API_URL;
const describeSystem = baseUrl === undefined ? describe.skip : describe;

const DEMO_PASSWORD = 'dukkan-demo-2026';
const MUNICH = { lat: 48.1374, lng: 11.5755 };
const RUN = `${Date.now()}`;

interface Session {
  api: DukkanApi;
  client: ApiClient;
}

function session(token: () => string | null = () => null, language = 'de'): Session {
  const client = createFetchApiClient({ baseUrl: baseUrl ?? '', getToken: token, getLanguage: () => language, fetchImpl: globalThis.fetch });
  return { api: createDukkanApi(client), client };
}

async function login(email: string, password = DEMO_PASSWORD): Promise<Session> {
  const auth = await session().api.login({ email, password });
  return session(() => auth.accessToken);
}

async function register(kind: 'CUSTOMER' | 'MERCHANT', firstName: string): Promise<Session> {
  const auth = await session().api.register({
    email: `system-${kind.toLowerCase()}-${RUN}@example.org`,
    password: `System-${RUN}-pass`,
    firstName,
    lastName: 'Systemtest',
    accountType: kind,
    preferredLocale: 'de',
  });
  return session(() => auth.accessToken);
}

function required<T>(value: T | null | undefined, what: string): T {
  if (value === null || value === undefined) {
    throw new Error(`missing ${what}`);
  }
  return value;
}

async function eventually<T>(read: () => Promise<T>, done: (value: T) => boolean, what: string): Promise<T> {
  for (let i = 0; i < 50; i++) {
    const value = await read();
    if (done(value)) {
      return value;
    }
    await new Promise<void>((resolve) => setTimeout(() => resolve(), 200));
  }
  throw new Error(`timed out waiting for ${what}`);
}

async function expectApiError(call: Promise<unknown>, status: number, code?: string): Promise<void> {
  const error: unknown = await call.then(
    () => undefined,
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(ApiError);
  expect((error as ApiError).status).toBe(status);
  if (code !== undefined) {
    expect((error as ApiError).code).toBe(code);
  }
}

const profile: MarketProfileRequest = {
  name: `Systemtest Markt ${RUN}`,
  typeCode: 'GROCERY',
  cuisineCodes: ['TURKISH'],
  description: { de: 'Markt aus dem Systemtest', en: 'Market from the system test' },
  primaryLocale: 'de',
  addressLine: 'Systemweg 1, 80331 München',
  location: MUNICH,
  tags: ['HALAL'],
  fulfilment: {
    delivery: false,
    pickup: true,
    minimumOrderValue: { amountCents: 500, currency: 'EUR' },
  },
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, opensAt: '07:00', closesAt: '22:00' })),
  phone: '+49 89 1234567',
  legalName: `Systemtest Markt ${RUN} GmbH`,
};

describeSystem('system test: from a new market to a packed order', () => {
  jest.setTimeout(120_000);

  // Shared across the steps below, which run in order and build on each other.
  let merchant: Session;
  let customer: Session;
  let marketId = '';
  const listing: Record<'cucumber' | 'tomatoes' | 'ayran', string> = { cucumber: '', tomatoes: '', ayran: '' };
  let order: Order;

  it('1. a merchant opens a market; an admin decides whether customers see it', async () => {
    merchant = await register('MERCHANT', 'Mira');
    const created = await merchant.api.createMyMarket(profile);
    marketId = created.id;
    const admin = await login('admin@dukkan.local');
    const setStatus = (status: string) => admin.client.request({ method: 'POST', path: `/admin/markets/${marketId}/status`, body: { status } });

    // New markets go live at once by default (dukkan.features.merchant-auto-approve); either way
    // a market that is not active stays hidden from customers.
    if (created.status === 'ACTIVE') {
      await setStatus('SUSPENDED');
    } else {
      expect(created.status).toBe('PENDING_REVIEW');
    }
    await expectApiError(session().api.getMarket(marketId), 404);
    await setStatus('ACTIVE');
    const visible = await session().api.getMarket(marketId);
    expect(visible.name).toBe(profile.name);
    // A new market is a showcase until the merchant switches online orders on.
    expect(visible.acceptsOrders).toBe(false);
    const settings = await merchant.api.getOrderSettings(marketId);
    expect(settings.ordersEnabled).toBe(false);
    await merchant.api.updateOrderSettings(marketId, { ordersEnabled: true, deliveryHours: settings.deliveryHours, autoAccept: false, paymentMethods: settings.paymentMethods });
    expect((await session().api.getMarket(marketId)).acceptsOrders).toBe(true);
  });

  it('2. the merchant lists products from the shared catalog and runs a "3 for 5 €" offer', async () => {
    const pick = async (name: string) =>
      required((await merchant.api.searchSharedCatalog({ q: name, marketId, size: 20 })).items.find((p) => p.name === name), name).id;
    const prices = { cucumber: ['Salatgurke', 70], tomatoes: ['Strauchtomaten', 250], ayran: ['Ayran', 200] } as const;
    for (const [key, [name, cents]] of Object.entries(prices) as [keyof typeof listing, readonly [string, number]][]) {
      const created = await merchant.api.createListing(marketId, { catalogProductId: await pick(name), price: { amountCents: cents, currency: 'EUR' } });
      listing[key] = created.id;
    }
    await merchant.api.createPromotion(marketId, {
      title: { de: '3 für 5' },
      type: 'MULTI_BUY',
      bundleQuantity: 3,
      bundlePrice: { amountCents: 500, currency: 'EUR' },
      validFrom: new Date(Date.now() - 60_000).toISOString(),
      validUntil: new Date(Date.now() + 7 * 86_400_000).toISOString(),
      productIds: [listing.ayran],
    });

    const categories = await session().api.listMarketCategories(marketId);
    expect(categories.filter((c) => c.parentId == null && (c.productCount ?? 0) > 0).length).toBeGreaterThanOrEqual(1);
    const ayran = await session().api.getProduct(listing.ayran);
    expect(ayran.promotion?.label).toBeDefined();
  });

  it('3. a customer reads a dictated shopping list and fills the cart with it', async () => {
    customer = await register('CUSTOMER', 'Kai');
    // The shopping list starts switched off; a platform admin switches it on for everyone.
    const admin = await login('admin@dukkan.local');
    await admin.client.request({ method: 'PUT', path: '/admin/features/SHOPPING_LIST', body: { enabled: true } });
    const capabilities = await session().api.getShoppingListCapabilities();
    expect(capabilities.enabled).toBe(true);
    const list = await customer.api.readShoppingList(marketId, { text: '2 Gurken, 1 kg Tomaten und 4 Ayran', images: [] });
    if (!capabilities.ai) {
      expect(list.engine).toBe('RULES');
    }
    const byProduct = new Map(list.lines.flatMap((line) => (line.match === null ? [] : [[line.match.product.id, line.match.quantity] as const])));
    // 1 kg with a 500 g pack is two packs.
    expect(Object.fromEntries(byProduct)).toEqual({ [listing.cucumber]: 2, [listing.tomatoes]: 2, [listing.ayran]: 4 });

    let cart: Cart | undefined;
    for (const [productId, quantity] of byProduct) {
      cart = await customer.api.setCartItemQuantity(marketId, productId, quantity);
    }
    const filled = required(cart, 'cart');
    // 2 × 0,70 + 2 × 2,50 + (5,00 for three Ayran + 2,00) = 13,40 €.
    expect(filled.total.amountCents).toBe(1340);
    expect(filled.meetsMinimumOrderValue).toBe(true);
  });

  it('4. the customer marks a favourite and places the order for pickup', async () => {
    await customer.api.addFavoriteProduct(listing.cucumber);
    expect((await customer.api.getProduct(listing.cucumber)).favorite).toBe(true);
    expect((await customer.api.listMarketFavorites(marketId)).flatMap((g) => g.items.map((p) => p.id))).toContain(listing.cucumber);
    // The start page is not opened before the order on purpose: its best-seller ranking is reused
    // for five minutes, and step 6 checks it right after the first sale.

    const slot = required((await customer.api.listSlots(marketId, 'PICKUP')).find((s) => s.available), 'available pickup slot');
    await customer.api.reserveFulfilment(marketId, { type: 'PICKUP', slotId: slot.id });
    order = await customer.api.placeOrder(marketId, `system-${RUN}`);
    expect(order.status).toBe('PLACED');
    expect(order.total.amountCents).toBe(1340);
    expect(order.originalTotal ?? null).toBeNull();
  });

  it('5. the merchant packs: one tomato pack and one Ayran are missing; the customer is told', async () => {
    const incoming = await merchant.api.listMarketOrders(marketId, 'PLACED');
    expect(incoming.map((o) => o.id)).toContain(order.id);
    await merchant.api.advanceOrderStatus(order.id, 'ACCEPTED');

    const line = (productId: string) => required(order.items.find((i) => i.productId === productId), productId).lineNo;
    const adjusted = await merchant.api.adjustOrderItems(order.id, [
      { lineNo: line(listing.tomatoes), fulfilledQuantity: 1 },
      { lineNo: line(listing.ayran), fulfilledQuantity: 3 },
    ]);
    // Three Ayran are exactly one "3 for 5 €" bundle: 1,40 + 2,50 + 5,00 = 8,90 €.
    expect(adjusted.total.amountCents).toBe(890);
    expect(adjusted.originalTotal?.amountCents).toBe(1340);

    const seen = await customer.api.getOrder(order.id);
    expect(seen.total.amountCents).toBe(890);
    expect(seen.items.find((i) => i.productId === listing.ayran)?.fulfilledQuantity).toBe(3);
    expect(seen.items.find((i) => i.productId === listing.cucumber)?.fulfilledQuantity ?? null).toBeNull();

    const inbox = await eventually(
      () => customer.api.listNotifications(),
      (page) => page.items.some((n) => n.kind === 'ORDER_ADJUSTED'),
      'the ORDER_ADJUSTED notification',
    );
    const note = required(inbox.items.find((n) => n.kind === 'ORDER_ADJUSTED'), 'notification');
    expect(note.title).toBe('Bestellung angepasst');
    expect(note.body).toContain('statt 13,40 €');
  });

  it('6. the order is handed over; it can no longer be changed and counts for the market', async () => {
    await merchant.api.advanceOrderStatus(order.id, 'READY_FOR_PICKUP');
    await expectApiError(merchant.api.adjustOrderItems(order.id, [{ lineNo: 1, fulfilledQuantity: 0 }]), 422, 'ORDER_NOT_ADJUSTABLE');
    expect((await merchant.api.advanceOrderStatus(order.id, 'COMPLETED')).status).toBe('COMPLETED');

    // "Meine Märkte": the market comes first, with what was bought there and the last order.
    const recent = await customer.api.listRecentMarkets([], MUNICH);
    const mine = required(recent[0], 'recent market');
    expect(mine.market.id).toBe(marketId);
    expect(mine.lastOrderId).toBe(order.id);
    expect(mine.recentProducts.map((p) => p.id).sort()).toEqual(Object.values(listing).sort());

    // Best sellers count what was handed over: 3 Ayran, 2 cucumbers, 1 tomato pack.
    const home = await session().api.getMarketHome(marketId);
    const best = required(home.sections.find((s) => s.key === 'BESTSELLERS'), 'best sellers section');
    expect(best.items.map((p) => p.id)).toEqual([listing.ayran, listing.cucumber, listing.tomatoes]);

    const insights = await merchant.client.request<{ topProducts: { listingId: string; quantity: number; revenue: { amountCents: number } }[] }>({
      method: 'GET',
      path: `/merchant/markets/${marketId}/insights`,
      query: { days: 30 },
    });
    const ayran = required(insights.topProducts.find((p) => p.listingId === listing.ayran), 'Ayran in insights');
    expect(ayran.quantity).toBe(3);
    expect(ayran.revenue.amountCents).toBe(500);
  });

  it('7. ownership holds: another merchant can neither see nor change the order', async () => {
    const stranger = await login('haendler@dukkan.local');
    await expectApiError(stranger.api.adjustOrderItems(order.id, [{ lineNo: 1, fulfilledQuantity: 1 }]), 404);
    await expectApiError(stranger.api.advanceOrderStatus(order.id, 'COMPLETED'), 404);
    await expectApiError(session().api.getOrder(order.id), 401);
  });
});
