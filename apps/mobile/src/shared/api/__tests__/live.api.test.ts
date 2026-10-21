/**
 * Live end-to-end check of the app's real API layer (`createFetchApiClient` + `createDukkanApi`)
 * against a running backend with the `local` profile (seed data + demo accounts).
 *
 * Skipped unless DUKKAN_LIVE_API_URL is set, e.g.:
 *   DUKKAN_LIVE_API_URL=http://localhost:8080/api/v1 npx jest live.api
 */
import { createFetchApiClient } from '../client';
import { createDukkanApi, type DukkanApi } from '../dukkanApi';
import type { MarketSummary } from '../types';

declare const process: { env: Record<string, string | undefined> };

const baseUrl = process.env.DUKKAN_LIVE_API_URL;

function required<T>(value: T | null | undefined, what: string): T {
  if (value === null || value === undefined) {
    throw new Error(`missing ${what}`);
  }
  return value;
}
const describeLive = baseUrl === undefined ? describe.skip : describe;

const ST_GEORG = { lat: 53.5553, lng: 10.0123 };
const DEMO_PASSWORD = 'dukkan-demo-2026';

function api(language: string, token: () => string | null = () => null): DukkanApi {
  return createDukkanApi(
    createFetchApiClient({
      baseUrl: baseUrl ?? '',
      getToken: token,
      getLanguage: () => language,
      fetchImpl: globalThis.fetch,
    }),
  );
}

async function signIn(email: string): Promise<DukkanApi> {
  const auth = await api('de').login({ email, password: DEMO_PASSWORD });
  return api('de', () => auth.accessToken);
}

function money(value: { amountCents: number; currency: string }): void {
  expect(Object.keys(value).sort()).toEqual(['amountCents', 'currency']);
  expect(Number.isInteger(value.amountCents)).toBe(true);
}

describeLive('live API (app client against the real backend)', () => {
  jest.setTimeout(60_000);

  it('serves meta, geo, discovery and market pages in every language', async () => {
    const de = api('de');
    const ar = api('ar');
    expect((await de.listLocales()).map((l) => l.code)).toEqual(['de', 'en', 'ar']);
    expect((await ar.listMarketTypes()).find((t) => t.code === 'BAKERY')?.name).toBe('مخبز');
    expect((await de.searchPlaces('st georg'))[0]?.district).toBe('St. Georg');

    const page = await de.listMarkets({ ...ST_GEORG, radiusKm: 5, size: 50 });
    expect(page.items.length).toBeGreaterThan(0);
    const distances = page.items.map((m) => m.distanceMeters ?? 0);
    expect([...distances].sort((a, b) => a - b)).toEqual(distances);

    const bakeries = await de.listMarkets({ ...ST_GEORG, radiusKm: 15, types: ['BAKERY', 'BUTCHER'] });
    expect(bakeries.items.length).toBeGreaterThan(0);
    expect(bakeries.items.every((m) => ['BAKERY', 'BUTCHER'].includes(m.type.code))).toBe(true);

    const market: MarketSummary = required(page.items[0], 'market');
    const home = await ar.getMarketHome(market.id);
    expect(home.sections.length).toBeGreaterThan(0);
    const firstProduct = required(home.sections[0]?.items[0], 'product');
    money(firstProduct.price);

    const categories = await de.listMarketCategories(market.id);
    expect(categories.every((c) => (c.productCount ?? 0) > 0)).toBe(true);
    const detail = await de.getProduct(firstProduct.id);
    expect(detail.market.id).toBe(market.id);

    for (const q of ['apfel', 'apple', 'تفاح']) {
      const offers = await de.searchProductsNearby({ q, ...ST_GEORG, radiusKm: 15 });
      expect(offers.items.map((o) => o.product.name)).toContain('Äpfel Elstar');
    }
  });

  it('runs the customer and merchant flows end to end', async () => {
    const customer = await signIn('kunde@dukkan.local');
    const merchant = await signIn('haendler@dukkan.local');
    const owned = await merchant.listMyMarkets();
    expect(owned.length).toBeGreaterThanOrEqual(1);
    const shop = required(owned.find((m) => m.fulfilment.pickup) ?? owned[0], 'owned market');

    // Fill the cart of one of the merchant's markets above its minimum order value.
    for (const cart of await customer.listCarts()) {
      await customer.clearCart(cart.market.id);
    }
    const products = await customer.listMarketProducts(shop.id, { sort: 'price_desc', size: 10 });
    let cart = await customer.getCart(shop.id);
    for (const product of products.items.filter((p) => p.available)) {
      if (cart.meetsMinimumOrderValue) {
        break;
      }
      cart = await customer.setCartItemQuantity(shop.id, product.id, Math.min(3, product.maxOrderQuantity));
    }
    expect(cart.meetsMinimumOrderValue).toBe(true);
    money(cart.total);
    expect((await customer.listCarts()).map((c) => c.market.id)).toContain(shop.id);

    // Reserve the first available slot of an offered type, with the default address for delivery.
    const type = shop.fulfilment.pickup ? 'PICKUP' : 'DELIVERY';
    const addressId = type === 'DELIVERY' ? (await customer.listAddresses())[0]?.id : undefined;
    const slots = await customer.listSlots(shop.id, type, addressId);
    const slot = required(slots.find((s) => s.available), 'available slot');
    cart = await customer.reserveFulfilment(shop.id, { type, slotId: slot.id, addressId: addressId ?? null });
    expect(cart.fulfilment?.slot.id).toBe(slot.id);

    const key = `live-${Date.now()}`;
    const order = await customer.placeOrder(shop.id, key);
    expect(order.status).toBe('PLACED');
    expect(order.total).toEqual(cart.total);
    expect((await customer.placeOrder(shop.id, key)).id).toBe(order.id);
    expect((await customer.getCart(shop.id)).items).toHaveLength(0);

    // The merchant sees and accepts it; the customer can still cancel before the cutoff.
    const incoming = await merchant.listMarketOrders(shop.id, 'PLACED');
    expect(incoming.map((o) => o.id)).toContain(order.id);
    expect((await merchant.advanceOrderStatus(order.id, 'ACCEPTED')).status).toBe('ACCEPTED');
    expect((await customer.cancelOrder(order.id)).status).toBe('CANCELLED');

    // Merchant adds a private product with three languages and finds it by an Arabic search.
    const category = required((await customer.listMarketCategories(shop.id))[0], 'category');
    const unique = `Livetest ${Date.now()}`;
    const listing = await merchant.createListing(shop.id, {
      newProduct: {
        name: { de: unique, en: `${unique} EN`, ar: 'منتج اختبار حي' },
        unitSize: '1 Stück',
        categoryId: category.id,
        keywords: ['livetest'],
      },
      price: { amountCents: 199, currency: 'EUR' },
    });
    expect(listing.privateProduct).toBe(true);
    const found = await api('de').listMarketProducts(shop.id, { q: 'منتج اختبار' });
    expect(found.items.map((p) => p.id)).toContain(listing.id);
    await merchant.deleteListing(shop.id, listing.id);
  });
});
