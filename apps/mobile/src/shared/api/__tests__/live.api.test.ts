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
import { blobOf } from '../../../test/formData';

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

// ---------------------------------------------------------------- tiny PNG encoder (no zlib needed)

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return c >>> 0;
});

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (const b of bytes) {
    c = (CRC_TABLE[(c ^ b) & 0xff] ?? 0) ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

const u32 = (n: number) => [(n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff];

function chunk(type: string, data: number[]): number[] {
  const body = Array.from({ length: type.length }, (_, i) => type.charCodeAt(i)).concat(data);
  return [...u32(data.length), ...body, ...u32(crc32(Uint8Array.from(body)))];
}

/** An RGB PNG of one colour, deflate "stored" blocks (valid zlib without compression). */
function solidPng(width: number, height: number, rgb: [number, number, number]): Uint8Array {
  const raw: number[] = [];
  for (let y = 0; y < height; y++) {
    raw.push(0); // filter: none
    for (let x = 0; x < width; x++) {
      raw.push(...rgb);
    }
  }
  let a = 1;
  let b = 0;
  for (const byte of raw) {
    a = (a + byte) % 65521;
    b = (b + a) % 65521;
  }
  const zlib = [0x78, 0x01, 0x01, raw.length & 0xff, raw.length >>> 8, ~raw.length & 0xff, (~raw.length >>> 8) & 0xff, ...raw, ...u32(((b << 16) | a) >>> 0)];
  const ihdr = [...u32(width), ...u32(height), 8, 2, 0, 0, 0];
  return Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, ...chunk('IHDR', ihdr), ...chunk('IDAT', zlib), ...chunk('IEND', [])]);
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
    expect(cart.items.every((i) => i.maxOrderQuantity >= i.quantity)).toBe(true);
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
    const allCategories = await api('ar').listCategories();
    expect(allCategories.length).toBeGreaterThanOrEqual(12);
    const category = required(allCategories.find((c) => c.parentId === null), 'category');
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

  it('uploads a photo as the demo merchant, attaches it to a listing and serves it publicly', async () => {
    const merchant = await signIn('haendler@dukkan.local');
    const shop = required((await merchant.listMyMarkets())[0], 'demo merchant market');
    const listing = required((await merchant.listMyListings(shop.id, { size: 1 })).items[0], 'listing');

    const seed = Date.now() % 250;
    const png = solidPng(12, 8, [seed, 120, 200]);
    const media = await merchant.uploadMedia(shop.id, { blob: blobOf(png, 'image/png'), name: 'live.png' }, 'PRODUCT');
    expect(media).toMatchObject({ width: 12, height: 8, contentType: 'image/png', sizeBytes: png.length });
    expect(media.url).toMatch(/^https?:\/\/.+\/api\/v1\/media\/[0-9a-f]{64}\.png$/);
    // Same bytes → same content-addressed URL.
    expect((await merchant.uploadMedia(shop.id, { blob: blobOf(png, 'image/png'), name: 'again.png' }, 'LOGO')).url).toBe(media.url);

    try {
      expect((await merchant.updateListing(shop.id, listing.id, { imageUrl: media.url })).imageUrl).toBe(media.url);
      const product = await api('en').getProduct(listing.id);
      expect(product.imageUrl).toBe(media.url);

      const served = await fetch(required(product.imageUrl, 'imageUrl'));
      expect(served.status).toBe(200);
      expect(served.headers.get('content-type')).toBe('image/png');
      expect(served.headers.get('cache-control')).toContain('immutable');
      expect(served.headers.get('x-content-type-options')).toBe('nosniff');
      expect(new Uint8Array(await served.arrayBuffer())).toEqual(png);
    } finally {
      // Seed listings have no own photo; null restores the catalog image.
      expect((await merchant.updateListing(shop.id, listing.id, { imageUrl: null })).imageUrl).not.toBe(media.url);
    }

    // Spoofed content: a text file declared as JPEG is rejected by its bytes.
    await expect(
      merchant.uploadMedia(shop.id, { blob: blobOf('<html></html>', 'image/jpeg'), name: 'fake.jpg' }, 'PRODUCT'),
    ).rejects.toMatchObject({ status: 415, code: 'UNSUPPORTED_MEDIA_TYPE' });
  });
});
