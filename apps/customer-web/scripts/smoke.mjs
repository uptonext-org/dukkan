// End-to-end smoke test of the built web app against a running backend (CI: e2e-api job).
// Serves dist/ with `vite preview` (which proxies /api) unless DUKKAN_WEB_URL points elsewhere.
//
//   npm run build && DUKKAN_SMOKE_PASSWORD=… npm run smoke
//
// Signs in as DUKKAN_SMOKE_EMAIL (default: the local profile's demo customer).
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

import { chromium } from 'playwright';

const BASE = process.env.DUKKAN_WEB_URL ?? 'http://localhost:5175';
const EMAIL = process.env.DUKKAN_SMOKE_EMAIL ?? 'kunde@dukkan.local';
const PASSWORD = process.env.DUKKAN_SMOKE_PASSWORD ?? 'dukkan-demo-2026';
const ST_GEORG = { latitude: 53.5536, longitude: 10.0136 };

async function reachable(url) {
  try {
    return (await fetch(url)).ok;
  } catch {
    return false;
  }
}

let server = null;
if (process.env.DUKKAN_WEB_URL === undefined && !(await reachable(BASE))) {
  server = spawn('npx', ['vite', 'preview', '--port', '5175', '--strictPort'], { stdio: 'ignore' });
  for (let i = 0; i < 60 && !(await reachable(BASE)); i++) {
    await sleep(500);
  }
}

const failures = [];
const step = async (name, run) => {
  try {
    await run();
    console.log(`ok   ${name}`);
  } catch (error) {
    failures.push(name);
    console.log(`FAIL ${name}: ${error instanceof Error ? error.message.split('\n')[0] : String(error)}`);
  }
};
const expect = (condition, message) => {
  if (!condition) throw new Error(message);
};

const browser = await chromium.launch(process.env.PLAYWRIGHT_BROWSERS_PATH === '/opt/pw-browsers' ? { executablePath: '/opt/pw-browsers/chromium' } : {});
const pageErrors = [];
try {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    geolocation: ST_GEORG,
    permissions: ['geolocation'],
  });
  // Map tiles come from a third party; the smoke test does not depend on them.
  await context.route(/tile\.openstreetmap\.org/, (route) => route.fulfill({ status: 204 }));
  const page = await context.newPage();
  page.on('pageerror', (error) => pageErrors.push(error.message));
  const visible = (selector) => page.locator(`${selector}:visible`).first();
  const dialogs = [];
  page.on('dialog', (dialog) => {
    dialogs.push(dialog.message());
    void dialog.accept();
  });

  let marketUrl = '';

  await step('first visit asks for the place and lists nearby markets after "use my location"', async () => {
    await page.goto(BASE);
    await page.waitForURL('**/welcome');
    await visible('[data-testid="use-location"]').click();
    await visible('[data-testid^="market-card-"]').waitFor();
    expect(new URL(page.url()).pathname === '/', `expected /, got ${page.url()}`);
  });

  await step('map mode shows one pin per market and the card of a pressed pin', async () => {
    await page.getByText('Karte', { exact: true }).click();
    // The pin element is zero-sized at the point; the app's pin view is drawn around it.
    await visible('[data-testid^="marker-"] > *').waitFor();
    expect((await page.locator('[data-testid^="marker-"]').count()) > 1, 'too few pins');
    await visible('[data-testid^="marker-"] > *').click();
    await visible('[data-testid="selected-market-card"]').waitFor();
    await page.getByText('Liste', { exact: true }).click();
  });

  await step('a market has its own address that survives a reload', async () => {
    await visible('[data-testid^="market-card-"]').click();
    await page.waitForURL(/\/markets\/[0-9a-f-]+$/);
    marketUrl = page.url();
    await page.reload();
    await visible('[data-testid^="product-tile-"]').waitFor();
    expect(page.url() === marketUrl, `reload moved to ${page.url()}`);
  });

  await step('a product opens at /markets/<id>/products/<id>', async () => {
    await visible('[data-testid^="product-tile-"] [role="button"]').click();
    await page.waitForURL(/\/products\/[0-9a-f-]+$/);
    await page.goBack();
    await page.waitForURL(marketUrl);
  });

  await step('sign-in keeps the session across a reload', async () => {
    await page.goto(`${BASE}/profile`);
    await page.getByLabel('E-Mail').first().fill(EMAIL);
    await page.getByLabel('Passwort').first().fill(PASSWORD);
    await visible('[data-testid="auth-submit"]').click();
    await visible('[data-testid="profile-orders"]').waitFor();
    await page.reload();
    await visible('[data-testid="profile-orders"]').waitFor();
  });

  await step('add to cart, then empty the cart through the confirmation dialog', async () => {
    await page.goto(marketUrl);
    await page.getByRole('button', { name: 'In den Warenkorb' }).first().click();
    await page.goto(`${marketUrl}/cart`);
    await visible('[data-testid="market-cart"]').waitFor();
    await visible('[data-testid="cart-more"]').click();
    await page.getByText('Dein Warenkorb ist leer').first().waitFor();
    expect(dialogs.some((d) => d.startsWith('Warenkorb leeren?')), `dialogs: ${dialogs.join(' / ')}`);
  });

  await step('Arabic mirrors the layout (right to left)', async () => {
    await page.goto(`${BASE}/profile`);
    await visible('[data-testid="profile-language"]').click();
    await page.getByText('العربية').first().click();
    await page.getByText('الملف الشخصي').first().waitFor();
    const rtl = await page.evaluate(() => [...document.querySelectorAll('#root div')].some((el) => getComputedStyle(el).direction === 'rtl'));
    expect(rtl, 'no right-to-left layout');
  });
  await context.close();
} finally {
  await browser.close();
  server?.kill();
}

if (pageErrors.length > 0) {
  console.log(`FAIL uncaught errors in the page:\n  ${pageErrors.join('\n  ')}`);
  failures.push('page errors');
}
console.log(failures.length === 0 ? 'Web smoke test passed.' : `Web smoke test failed: ${failures.join(', ')}`);
process.exit(failures.length === 0 ? 0 : 1);
