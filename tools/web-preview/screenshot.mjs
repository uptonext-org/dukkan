// Drives the web preview of apps/mobile in headless Chromium and saves screenshots for visual QA.
//
//   npm run build && npm run screenshots            # all variants
//   ONLY=de-light npm run screenshots                # one variant
//
// Needs the backend at DUKKAN_API_TARGET (default http://localhost:8080) with seed data. The
// script starts `vite preview` on PREVIEW_PORT itself unless PREVIEW_URL points at a running one.
import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= '/opt/pw-browsers';
const { chromium } = await import('playwright');

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(here, 'screenshots');
const PORT = Number(process.env.PREVIEW_PORT ?? 5199);
const BASE = process.env.PREVIEW_URL ?? `http://localhost:${PORT}`;
const API = `${process.env.DUKKAN_API_TARGET ?? 'http://localhost:8080'}/api/v1`;
const PASSWORD = 'dukkan-demo-2026';
const CUSTOMER = 'kunde@dukkan.local';
const MERCHANT = 'haendler@dukkan.local';
const MARKET_NAME = 'Anatolia Frischmarkt';

// Fixed St. Georg position (as chosen in onboarding) for every variant after the first.
const ST_GEORG = { label: 'St. Georg, Hamburg', shortLabel: 'St. Georg', location: { lat: 53.5551, lng: 10.0128 } };
const KEYCHAIN_KEY = 'rn-keychain:app.dukkan.mobile.auth';

const VARIANTS = [
  { lang: 'de', theme: 'light', full: true },
  { lang: 'ar', theme: 'light', full: false },
  { lang: 'de', theme: 'dark', full: false },
].filter((v) => !process.env.ONLY || process.env.ONLY.split(',').includes(`${v.lang}-${v.theme}`));

// ---------------------------------------------------------------------------------------------
// Backend helpers (setup only: tokens, a clean cart, a few favourites)

async function api(method, pathname, { token, body } = {}) {
  const res = await fetch(API + pathname, {
    method,
    headers: {
      'content-type': 'application/json',
      'accept-language': 'de',
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`${method} ${pathname} → ${res.status} ${await res.text()}`);
  }
  return res.status === 204 || res.status === 404 ? null : res.json().catch(() => null);
}
const login = async (email) => (await api('POST', '/auth/login', { body: { email, password: PASSWORD } })).accessToken;

// ---------------------------------------------------------------------------------------------
// Preview server

async function reachable(url) {
  try {
    return (await fetch(url)).ok;
  } catch {
    return false;
  }
}
async function startPreview() {
  if (await reachable(BASE)) return null;
  const child = spawn('npx', ['vite', 'preview', '--port', String(PORT), '--strictPort'], { cwd: here, stdio: 'ignore' });
  for (let i = 0; i < 60; i++) {
    if (await reachable(BASE)) return child;
    await new Promise((r) => setTimeout(r, 500));
  }
  child.kill();
  throw new Error(`vite preview did not start on ${BASE} (did you run npm run build?)`);
}

// ---------------------------------------------------------------------------------------------
// Page helpers

const settle = async (page, ms = 700) => {
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await page.waitForTimeout(ms);
};

/** Bottom tab of the visible tab bar, by position (labels differ per language). */
const tab = (page, index) => page.locator('a[role="tab"]:visible').nth(index);

const testId = (page, id) => page.locator(`[data-testid="${id}"]:visible`).first();

/** The input inside a SearchField / TextField with the given testID. */
const inputOf = (page, id) => page.locator(`input[data-testid="${id}"]:visible, [data-testid="${id}"]:visible input`).first();

/** Scrolls the innermost visible scroll container (RN ScrollView/FlatList) by `dy`. */
async function scrollMain(page, dy) {
  await page.evaluate((delta) => {
    const candidates = [...document.querySelectorAll('div')].filter((el) => {
      const s = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return (s.overflowY === 'auto' || s.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 4 && r.width > 300 && r.height > 300 && r.bottom > 0;
    });
    const target = candidates.sort((a, b) => b.getBoundingClientRect().height - a.getBoundingClientRect().height).at(-1);
    target?.scrollBy(0, delta);
  }, dy);
  await page.waitForTimeout(400);
}

const AUTH_STORAGE = (token) => JSON.stringify({ username: 'dukkan', password: token });

async function newContext(browser, variant, { token = null, place = true } = {}) {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    colorScheme: variant.theme,
    locale: variant.lang === 'ar' ? 'ar' : 'de-DE',
    timezoneId: 'Europe/Berlin',
    geolocation: { latitude: ST_GEORG.location.lat, longitude: ST_GEORG.location.lng },
    permissions: ['geolocation'],
  });
  const seed = {
    'rn-async:dukkan.language': variant.lang,
    ...(place ? { 'rn-async:dukkan.place': JSON.stringify(ST_GEORG) } : {}),
    ...(token ? { [KEYCHAIN_KEY]: AUTH_STORAGE(token) } : {}),
  };
  // Seed only on the first load so in-app changes survive reloads within one context.
  await context.addInitScript((entries) => {
    if (sessionStorage.getItem('seeded') === null) {
      localStorage.clear();
      for (const [k, v] of Object.entries(entries)) localStorage.setItem(k, v);
      sessionStorage.setItem('seeded', '1');
    }
  }, seed);
  return context;
}

async function openPage(context, errors) {
  const page = await context.newPage();
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`console: ${m.text().slice(0, 300)}`);
  });
  await page.goto(BASE);
  await settle(page, 1000);
  return page;
}

// ---------------------------------------------------------------------------------------------
// Flows

async function main() {
  await mkdir(OUT, { recursive: true });
  const server = await startPreview();
  const browser = await chromium.launch();
  const saved = [];
  const errors = [];

  const customerToken = await login(CUSTOMER);
  const merchantToken = await login(MERCHANT);
  const markets = await api('GET', `/markets?lat=${ST_GEORG.location.lat}&lng=${ST_GEORG.location.lng}`);
  const market = markets.items.find((m) => m.name === MARKET_NAME);
  if (!market) throw new Error(`${MARKET_NAME} not in seed data`);

  // Favourites for screenshot 7: the first two products of the market.
  const products = await api('GET', `/markets/${market.id}/products?size=6`, { token: customerToken });
  for (const p of (products.items ?? products.content ?? []).slice(0, 2)) {
    await api('PUT', `/favorites/products/${p.id}`, { token: customerToken });
  }

  try {
    for (const variant of VARIANTS) {
      const prefix = `${variant.lang}-${variant.theme}`;
      const shot = async (page, nn, name) => {
        const file = path.join(OUT, `${prefix}-${nn}-${name}.png`);
        await page.screenshot({ path: file });
        saved.push(path.basename(file));
        console.log('  saved', path.basename(file));
      };
      const step = async (label, fn) => {
        try {
          await fn();
        } catch (e) {
          errors.push(`[${prefix}] ${label}: ${e.message.split('\n')[0]}`);
          console.log(`  FAILED ${label}: ${e.message.split('\n')[0]}`);
        }
      };
      console.log(`== ${prefix}`);

      // ---- Guest flows: 1 onboarding, 2 marketplace, 3–5 market sub-app, 8 global search
      {
        const context = await newContext(browser, variant, { place: !variant.full });
        const page = await openPage(context, errors);

        if (variant.full) {
          await step('01 onboarding', async () => {
            await testId(page, 'place-search').waitFor();
            await shot(page, '01', 'onboarding');
            await inputOf(page, 'place-search').fill('st georg');
            await page.locator('[data-testid="place-suggestion"]').first().waitFor();
            await settle(page, 400);
            await shot(page, '01', 'onboarding-search');
            await page.locator('[data-testid="place-suggestion"]').first().click();
            await testId(page, 'market-list').waitFor();
          });
        }

        await step('02 discover', async () => {
          await testId(page, 'market-list').waitFor();
          await settle(page);
          await shot(page, '02', 'discover-list');
          if (variant.full) {
            await scrollMain(page, 700);
            await shot(page, '02', 'discover-list-scrolled');
            await scrollMain(page, -5000);
          }
          await testId(page, 'filter-button').click();
          await testId(page, 'filter-sheet').waitFor();
          await settle(page, 500);
          await shot(page, '02', 'discover-filter-sheet');
          await page.keyboard.press('Escape');
          await settle(page, 300);
          if (await testId(page, 'filter-sheet').isVisible()) {
            await page.locator('[data-testid="filter-sheet"] [role="button"]').first().click();
          }
          await page.locator('[role="tablist"] [role="tab"]:visible').nth(1).click();
          await testId(page, 'market-map').waitFor();
          await settle(page);
          await shot(page, '02', 'discover-map');
          await page.locator('[data-testid^="marker-"]:visible').first().click();
          await testId(page, 'selected-market-card').waitFor();
          await settle(page, 300);
          await shot(page, '02', 'discover-map-selected');
          await page.locator('[role="tablist"] [role="tab"]:visible').nth(0).click();
          await testId(page, 'market-list').waitFor();
        });

        await step('03 market home', async () => {
          await testId(page, `market-card-${market.id}`).click();
          await testId(page, 'market-home').waitFor();
          await page.locator('[data-testid="banner-carousel"]:visible').waitFor();
          await settle(page, 1000);
          await shot(page, '03', 'market-home');
          await scrollMain(page, 600);
          await shot(page, '03', 'market-home-sections');
          if (variant.full) {
            await scrollMain(page, 700);
            await shot(page, '03', 'market-home-sections-2');
          }
          await scrollMain(page, -5000);
        });

        await step('04 market search', async () => {
          await tab(page, 2).click();
          await page.locator('[data-testid^="category-"]:visible').first().waitFor();
          await settle(page);
          await shot(page, '04', 'market-search');
          await page.locator('[data-testid^="category-"]:visible').first().click();
          await page.locator('[data-testid^="product-tile-"]:visible').first().waitFor();
          await settle(page, 900);
          await shot(page, '04', 'category-results');
        });

        await step('05 product detail', async () => {
          await page.locator('[data-testid^="product-tile-"]:visible [role="button"]').first().click();
          await testId(page, 'product-detail').waitFor();
          await settle(page, 900);
          await shot(page, '05', 'product-detail');
          if (variant.full) {
            await scrollMain(page, 600);
            await shot(page, '05', 'product-detail-scrolled');
          }
        });

        if (variant.full) {
          await step('05 guest add → sign-in sheet', async () => {
            await page.locator('[data-testid="product-detail"]:visible').waitFor();
            await page.getByRole('button', { name: 'In den Warenkorb' }).first().click();
            await testId(page, 'auth-sheet').waitFor();
            await settle(page, 500);
            await shot(page, '05', 'guest-add-auth-sheet');
          });
        }

        await step('08 global search', async () => {
          await page.goto(BASE);
          await settle(page, 1000);
          await tab(page, 1).click();
          await inputOf(page, 'global-search-field').waitFor();
          if (variant.full) await shot(page, '08', 'global-search-empty');
          await inputOf(page, 'global-search-field').fill('apfel');
          await page.locator('[data-testid^="offer-"]:visible').first().waitFor();
          await settle(page, 900);
          await shot(page, '08', 'global-search-apfel');
        });
        await context.close();
      }

      // ---- Signed in as customer: 6 cart + fulfilment, 7 favourites, 9 profile
      {
        await api('DELETE', `/markets/${market.id}/cart`, { token: customerToken });
        const context = await newContext(browser, variant, { token: customerToken });
        const page = await openPage(context, errors);

        await step('06 fulfilment with empty cart', async () => {
          // The cart was cleared above: reserving would be 422 CART_EMPTY, so the sheet shows the
          // slots read-only with a hint.
          await testId(page, `market-card-${market.id}`).click();
          await testId(page, 'market-home').waitFor();
          await settle(page, 900);
          await testId(page, 'fulfilment-link').click();
          await testId(page, 'fulfilment-sheet').waitFor();
          await testId(page, 'fulfilment-empty-hint').waitFor();
          await settle(page, 900);
          await shot(page, '06', 'fulfilment-empty-cart');
          await page.keyboard.press('Escape');
          await settle(page, 300);
          if (await testId(page, 'fulfilment-sheet').isVisible()) {
            await page.locator('[data-testid="fulfilment-sheet"] [role="button"]').first().click();
            await settle(page, 300);
          }
        });

        await step('06 cart', async () => {
          if (!(await testId(page, 'market-home').isVisible())) {
            await testId(page, `market-card-${market.id}`).click();
            await testId(page, 'market-home').waitFor();
          }
          await settle(page, 900);
          const tiles = page.locator('[data-testid^="product-tile-"]:visible');
          const seen = new Set();
          for (let i = 0, added = 0; added < 3 && i < (await tiles.count()); i++) {
            const tile = tiles.nth(i);
            const id = await tile.getAttribute('data-testid');
            if (seen.has(id)) continue;
            seen.add(id);
            await tile.scrollIntoViewIfNeeded();
            await tile.locator('[role="button"]').last().click();
            await page.waitForTimeout(900);
            added++;
          }
          await settle(page, 600);
          await shot(page, '06', 'market-home-added');
          await tab(page, 3).click();
          await testId(page, 'market-cart').waitFor();
          await settle(page, 900);
          await shot(page, '06', 'cart');
          await testId(page, 'fulfilment-bar').click();
          await testId(page, 'fulfilment-sheet').waitFor();
          await settle(page, 900);
          await shot(page, '06', 'fulfilment-sheet');
          await page.locator('[data-testid="fulfilment-sheet"] [aria-label]').first().click({ trial: true }).catch(() => undefined);
        });

        if (variant.full) {
          await step('07 favourites', async () => {
            await page.goto(BASE);
            await settle(page, 1000);
            await testId(page, `market-card-${market.id}`).click();
            await testId(page, 'market-home').waitFor();
            await tab(page, 1).click();
            await testId(page, 'market-favorites').waitFor();
            await settle(page, 900);
            await shot(page, '07', 'favorites');
            await scrollMain(page, 600);
            await shot(page, '07', 'favorites-scrolled');
          });

          await step('06 carts tab', async () => {
            await page.goto(BASE);
            await settle(page, 1000);
            await tab(page, 2).click();
            await page.locator(`[data-testid="cart-summary-${market.id}"]:visible`).waitFor();
            await settle(page);
            await shot(page, '06', 'carts-tab');
          });

          await step('09 profile', async () => {
            await tab(page, 3).click();
            await testId(page, 'profile-language').waitFor();
            await settle(page);
            await shot(page, '09', 'profile-customer');
            await testId(page, 'profile-language').click();
            await testId(page, 'language-sheet').waitFor();
            await settle(page, 500);
            await shot(page, '09', 'language-sheet');
          });
        }
        await context.close();
      }

      // ---- Merchant: 9 Mein Markt, listings, Produkt hinzufügen
      if (variant.full) {
        const context = await newContext(browser, variant, { token: merchantToken });
        const page = await openPage(context, errors);
        await step('09 merchant', async () => {
          await tab(page, 3).click();
          await testId(page, 'profile-my-market').waitFor();
          await settle(page);
          await shot(page, '09', 'profile-merchant');
          await testId(page, 'profile-my-market').click();
          await page.getByText('Meine Märkte').or(page.locator('[role="button"]:visible')).first().waitFor();
          await settle(page, 900);
          await shot(page, '09', 'merchant-markets');
          await page.getByRole('button', { name: new RegExp(MARKET_NAME) }).first().click();
          await testId(page, 'hub-listings').waitFor();
          await settle(page);
          await shot(page, '09', 'merchant-hub');
          await testId(page, 'hub-listings').click();
          await page.locator('[data-testid^="availability-"]:visible').first().waitFor();
          await settle(page, 900);
          await shot(page, '09', 'merchant-listings');
          await page.getByRole('button', { name: 'Produkt hinzufügen' }).last().click();
          await testId(page, 'catalog-search').waitFor();
          await settle(page, 900);
          await shot(page, '09', 'merchant-add-product');
        });
        await context.close();
      }
    }
  } finally {
    await browser.close();
    server?.kill();
  }

  console.log(`\n${saved.length} screenshots in ${OUT}`);
  if (errors.length > 0) {
    console.log(`\n${errors.length} problems:`);
    for (const e of [...new Set(errors)]) console.log(' -', e);
    process.exitCode = 1;
  }
}

await main();
