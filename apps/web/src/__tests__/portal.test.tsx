import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { newArrivals } from '../alerts/OrderAlerts';
import { App } from '../App';
import type { Session } from '../api/client';
import type { CatalogReviewItem } from '../api/types';
import { applyLanguage } from '../i18n';
import { CSV_TEMPLATE } from '../pages/ImportPage';
import { change } from '../pages/InsightsPage';
import { nextStatuses } from '../pages/OrdersBoard';
import { niceTicks } from '../ui/ColumnChart';
import { adminUser, fakeBackend, market, memoryStore, merchantUser, respond } from '../test/fakeBackend';
import { parsePrice } from '../ui/format';

const merchantSession: Session = { accessToken: 'access-1', refreshToken: 'refresh-1', user: merchantUser };

function renderPortal(routes: Parameters<typeof fakeBackend>[0], session: Session | null = merchantSession) {
  const backend = fakeBackend(routes);
  const store = memoryStore(session);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { gcTime: Infinity } } });
  render(<App dependencies={{ fetch: backend.fetch, store, queryClient }} />);
  return { ...backend, store, user: userEvent.setup() };
}

const order = {
  id: 'o1',
  orderNumber: 'DK-20261021-000007',
  status: 'PLACED',
  placedAt: '2026-10-21T08:00:00Z',
  fulfilmentType: 'PICKUP',
  items: [{ lineNo: 1, productId: 'p1', name: 'Ayran', quantity: 2, fulfilledQuantity: null, lineTotal: { amountCents: 198, currency: 'EUR' } }],
  total: { amountCents: 198, currency: 'EUR' },
  slot: { startsAt: '2026-10-21T14:00:00Z', endsAt: '2026-10-21T15:00:00Z' },
  customerName: 'Leyla A.',
};

describe('sign-in', () => {
  it('signs a merchant in and opens the orders board', async () => {
    const { user, calls, store } = renderPortal(
      {
        'POST /auth/login': { accessToken: 'a', expiresIn: 900, refreshToken: 'r', refreshExpiresIn: 1, user: merchantUser },
        'GET /merchant/markets': [market()],
        'GET /merchant/markets/m1/orders': [order],
      },
      null,
    );
    await user.type(screen.getByLabelText('E-Mail'), 'mert@example.de');
    await user.type(screen.getByLabelText('Passwort'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Anmelden' }));

    expect(await screen.findByRole('heading', { name: 'Bestellungen' })).toBeInTheDocument();
    expect(await screen.findByRole('article', { name: 'DK-20261021-000007' })).toHaveTextContent('Leyla A.');
    expect(store.current?.refreshToken).toBe('r');
    expect(calls.find((c) => c.path === '/merchant/markets')?.headers.Authorization).toBe('Bearer a');
  });

  it('turns away customer accounts', async () => {
    const { user, store, calls } = renderPortal(
      {
        'POST /auth/login': { accessToken: 'a', expiresIn: 900, refreshToken: 'r', refreshExpiresIn: 1, user: { ...merchantUser, roles: ['CUSTOMER'] } },
        'POST /auth/logout': respond(204),
      },
      null,
    );
    await user.type(screen.getByLabelText('E-Mail'), 'kunde@example.de');
    await user.type(screen.getByLabelText('Passwort'), 'x');
    await user.click(screen.getByRole('button', { name: 'Anmelden' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('keinen Zugang');
    expect(store.current).toBeNull();
    expect(calls.some((c) => c.path === '/auth/logout')).toBe(true);
  });

  it('shows wrong credentials', async () => {
    const { user } = renderPortal({ 'POST /auth/login': respond(401, { status: 401, code: 'INVALID_CREDENTIALS' }) }, null);
    await user.type(screen.getByLabelText('E-Mail'), 'a@b.de');
    await user.type(screen.getByLabelText('Passwort'), 'x');
    await user.click(screen.getByRole('button', { name: 'Anmelden' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('E-Mail oder Passwort ist falsch.');
  });

  it('renews an expired access token once and retries', async () => {
    let refreshed = false;
    const { calls, store } = renderPortal({
      'GET /merchant/markets': (call) => (call.headers.Authorization === 'Bearer access-2' ? [market()] : respond(401, { status: 401, code: 'UNAUTHORIZED' })),
      'POST /auth/refresh': (call) => {
        expect(call.body).toEqual({ refreshToken: 'refresh-1' });
        refreshed = true;
        return { accessToken: 'access-2', expiresIn: 900, refreshToken: 'refresh-2', refreshExpiresIn: 1, user: merchantUser };
      },
      'GET /merchant/markets/m1/orders': [],
    });
    expect(await screen.findByText('Anatolia Frischmarkt · Aktiv')).toBeInTheDocument();
    expect(refreshed).toBe(true);
    expect(store.current?.refreshToken).toBe('refresh-2');
    expect(calls.filter((c) => c.path === '/auth/refresh')).toHaveLength(1);
  });
});

describe('orders board', () => {
  it('moves an order through its states', async () => {
    let current = { ...order };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': () => [current],
      'POST /merchant/orders/o1/status': (call) => {
        current = { ...current, status: (call.body as { status: string }).status };
        return current;
      },
    });
    const card = await screen.findByRole('article', { name: 'DK-20261021-000007' });
    expect(within(card).getByRole('button', { name: 'Ablehnen' })).toBeInTheDocument();
    await user.click(within(card).getByRole('button', { name: 'Annehmen' }));
    await waitFor(() => expect(within(screen.getByRole('region', { name: /Angenommen/ })).getByRole('article')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Bereit zur Abholung' }));
    await waitFor(() => expect(calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([{ status: 'ACCEPTED' }, { status: 'READY_FOR_PICKUP' }]));
  });

  it('enters a missing item while packing; the card shows the new total', async () => {
    let current: Record<string, unknown> = {
      ...order,
      status: 'ACCEPTED',
      items: [...order.items, { lineNo: 2, productId: 'p2', name: 'Simit', quantity: 3, fulfilledQuantity: null, lineTotal: { amountCents: 300, currency: 'EUR' } }],
      total: { amountCents: 498, currency: 'EUR' },
    };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': () => [current],
      'PUT /merchant/orders/o1/items': () => {
        current = {
          ...current,
          items: [order.items[0], { lineNo: 2, productId: 'p2', name: 'Simit', quantity: 3, fulfilledQuantity: 1, lineTotal: { amountCents: 100, currency: 'EUR' } }],
          total: { amountCents: 298, currency: 'EUR' },
          originalTotal: { amountCents: 498, currency: 'EUR' },
        };
        return current;
      },
    });
    const card = await screen.findByRole('article', { name: 'DK-20261021-000007' });
    await user.click(within(card).getByRole('button', { name: 'Fehlmengen eintragen' }));
    const simit = within(card).getByRole('spinbutton', { name: 'Simit: herausgegeben' });
    await user.clear(simit);
    await user.type(simit, '1');
    await user.click(within(card).getByRole('button', { name: 'Speichern' }));

    // Only the line that was changed is sent; clearing a field on the way does not mean "missing".
    await waitFor(() => expect(calls.filter((c) => c.method === 'PUT').map((c) => c.body)).toEqual([{ items: [{ lineNo: 2, fulfilledQuantity: 1 }] }]));
    expect(await within(card).findByText('1 von 3')).toBeInTheDocument();
    expect(within(card).getByText(/statt 4,98/)).toBeInTheDocument();
  });

  it('knows the next statuses per fulfilment type', () => {
    expect(nextStatuses({ ...order, status: 'ACCEPTED', fulfilmentType: 'DELIVERY' } as never)).toEqual(['OUT_FOR_DELIVERY']);
    expect(nextStatuses({ ...order, status: 'COMPLETED' } as never)).toEqual([]);
  });
});

describe('assortment', () => {
  it('saves a changed price and availability', async () => {
    const listing = { id: 'l1', name: 'Ayran', brand: 'Sütaş', unitSize: '250 ml', price: { amountCents: 99, currency: 'EUR' }, available: true, maxOrderQuantity: 24, privateProduct: false };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/listings': { items: [listing], page: 0, size: 50, totalItems: 1, totalPages: 1 },
      'PUT /merchant/markets/m1/listings/l1': (call) => ({ ...listing, price: (call.body as { price: unknown }).price }),
    });
    await user.click(await screen.findByRole('button', { name: 'Sortiment' }));
    const price = await screen.findByLabelText('Preis (€): Ayran');
    expect(price).toHaveValue('0,99');
    await user.clear(price);
    await user.type(price, '1,19');
    await user.click(screen.getByLabelText('Verfügbar: Ayran'));
    await user.click(screen.getByRole('button', { name: 'Speichern' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ price: { amountCents: 119, currency: 'EUR' }, available: false }),
    );
    // Changing the language keeps the page and its search.
    await user.type(screen.getByLabelText('Suchen'), 'Ayr');
    await user.selectOptions(screen.getByLabelText('Sprache'), 'en');
    expect(await screen.findByLabelText('Search')).toHaveValue('Ayr');
    applyLanguage('de');
    await user.clear(price);
    await user.type(price, 'gratis');
    expect(screen.getByText('Preis wie 4,49 eingeben.')).toBeInTheDocument();
  });
});

describe('own details of a catalog product', () => {
  it('adjusts a listing for this market only; empty fields go back to the catalog', async () => {
    const listing = { id: 'l1', name: 'Ayran', brand: 'Sütaş', unitSize: '250 ml', price: { amountCents: 99, currency: 'EUR' }, available: true, maxOrderQuantity: 24, privateProduct: false };
    const own = { ...listing, id: 'l2', name: 'Omas Simit', privateProduct: true };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/listings': { items: [listing, own], page: 0, size: 50, totalItems: 2, totalPages: 1 },
      'PUT /merchant/markets/m1/listings/l1': (call) => ({ ...listing, overrides: (call.body as { overrides: unknown }).overrides }),
    });
    await user.click(await screen.findByRole('button', { name: 'Sortiment' }));
    // Only catalog products can be adjusted; a private product is the market's own anyway.
    expect(await screen.findAllByText('Für meinen Markt anpassen')).toHaveLength(1);
    await user.click(screen.getByText('Für meinen Markt anpassen'));
    expect(screen.getByText(/der gemeinsame Katalog bleibt unverändert/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Name'), ' Hausgemachter Ayran ');
    await user.type(screen.getByLabelText('Größe'), '500 ml');
    await user.click(screen.getByRole('button', { name: 'Angaben speichern' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ overrides: { name: 'Hausgemachter Ayran', unitSize: '500 ml', brand: null, description: null } }),
    );
  });
});

describe('customer feedback', () => {
  it('switches feedback on and shows the messages with first name and rating', async () => {
    let enabled = false;
    const messages = [{ id: 'f1', customerName: 'Ada', rating: 4, message: 'Bitte mehr Bio-Joghurt!', createdAt: '2026-10-20T09:30:00Z' }];
    const pageOf = () => ({ enabled, feedback: { items: enabled ? messages : [], page: 0, size: 20, totalItems: enabled ? 1 : 0, totalPages: 1 } });
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/feedback?page=0&size=20': pageOf,
      'PUT /merchant/markets/m1/feedback/settings': (call) => {
        enabled = (call.body as { enabled: boolean }).enabled;
        return pageOf();
      },
    });
    await user.click(await screen.findByRole('button', { name: 'Kundenfeedback' }));
    expect(await screen.findByText(/Noch kein Feedback/)).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Feedback von Kunden annehmen' }));
    expect(await screen.findByText('Bitte mehr Bio-Joghurt!')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ enabled: true });
    expect(screen.getByText(/Ada · .* · 4 von 5 Sternen/)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Feedback von Kunden annehmen' })).toBeChecked();
  });
});

describe('CSV import', () => {
  const preview = {
    dryRun: true,
    applied: false,
    created: 1,
    listed: 1,
    updated: 0,
    failed: 0,
    rows: [
      { line: 2, action: 'LIST', name: 'Äpfel', listingId: null, errors: [] },
      { line: 3, action: 'CREATE', name: 'Simit', listingId: null, errors: [] },
    ],
  };

  it('checks the file first and imports only after confirmation', async () => {
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'POST /merchant/markets/m1/listings/import?dryRun=true': preview,
      'POST /merchant/markets/m1/listings/import?dryRun=false': { ...preview, dryRun: false, applied: true, rows: preview.rows.map((r, i) => ({ ...r, listingId: `l${i}` })) },
    });
    await user.click(await screen.findByRole('button', { name: 'CSV-Import' }));
    await user.upload(screen.getByLabelText('CSV-Datei'), new File([CSV_TEMPLATE], 'produkte.csv', { type: 'text/csv' }));
    await user.click(screen.getByRole('button', { name: 'Datei prüfen' }));

    expect(await screen.findByText('1 neu · 1 aus dem Katalog · 0 aktualisiert · 0 mit Fehlern')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Simit' })).toBeInTheDocument();
    const upload = calls.find((c) => c.path.endsWith('dryRun=true'));
    expect(upload?.body).toBeInstanceOf(FormData);
    expect((upload?.body as FormData).get('file')).toBeInstanceOf(File);

    await user.click(screen.getByRole('button', { name: '2 Zeilen übernehmen' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Fertig: 2 Zeilen übernommen.');
  });

  it('lists the problems and offers no import when a row is wrong', async () => {
    const { user } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'POST /merchant/markets/m1/listings/import?dryRun=true': {
        ...preview,
        failed: 1,
        rows: [{ line: 4, action: 'ERROR', name: 'Börek', listingId: null, errors: [{ field: 'category', code: 'UnknownCode', message: 'a known category is required' }] }],
      },
    });
    await user.click(await screen.findByRole('button', { name: 'CSV-Import' }));
    await user.upload(screen.getByLabelText('CSV-Datei'), new File(['x'], 'p.csv', { type: 'text/csv' }));
    await user.click(screen.getByRole('button', { name: 'Datei prüfen' }));
    expect(await screen.findByText(/Bitte korrigiere die markierten Zeilen/)).toBeInTheDocument();
    expect(screen.getByText('category')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /übernehmen/ })).toBeNull();
  });

  it('explains file-level problems', async () => {
    const { user } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'POST /merchant/markets/m1/listings/import?dryRun=true': respond(400, {
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'file', code: 'Encoding', message: 'the file must be UTF-8' }],
      }),
    });
    await user.click(await screen.findByRole('button', { name: 'CSV-Import' }));
    await user.upload(screen.getByLabelText('CSV-Datei'), new File(['x'], 'p.csv', { type: 'text/csv' }));
    await user.click(screen.getByRole('button', { name: 'Datei prüfen' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('CSV UTF-8');
  });
});

describe('admin market review', () => {
  it('approves a market waiting for review', async () => {
    let status = 'PENDING_REVIEW';
    const { user, calls } = renderPortal(
      {
        'GET /admin/markets': () => (status === 'PENDING_REVIEW' ? [market({ status })] : []),
        'GET /admin/markets?status=PENDING_REVIEW': () => (status === 'PENDING_REVIEW' ? [market({ status })] : []),
        'POST /admin/markets/m1/status': (call) => {
          status = (call.body as { status: string }).status;
          return market({ status });
        },
      },
      { accessToken: 'a', refreshToken: 'r', user: adminUser },
    );
    const card = await screen.findByRole('article', { name: 'Anatolia Frischmarkt' });
    expect(within(card).getByText('Anatolia GmbH', { exact: false })).toBeInTheDocument();
    await user.click(within(card).getByRole('button', { name: 'Freigeben' }));
    expect(await screen.findByText('Keine Märkte mit diesem Status.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ status: 'ACTIVE' });
    // Admins without a market see no merchant tabs.
    expect(screen.queryByRole('button', { name: 'Bestellungen' })).toBeNull();
  });
  it('shows what seller information is missing and does not approve an incomplete market', async () => {
    const incomplete = market({
      status: 'PENDING_REVIEW',
      phone: null,
      review: {
        complete: false,
        items: [
          { code: 'LEGAL_NAME', ok: true, required: true },
          { code: 'POSTAL_ADDRESS', ok: true, required: true },
          { code: 'PHONE', ok: false, required: true },
          { code: 'REGISTER_INFO', ok: false, required: false },
        ],
      },
    });
    renderPortal(
      { 'GET /admin/markets?status=PENDING_REVIEW': [incomplete] },
      { accessToken: 'a', refreshToken: 'r', user: adminUser },
    );
    const card = await screen.findByRole('article', { name: 'Anatolia Frischmarkt' });
    const checklist = within(card).getByRole('list', { name: 'Prüfliste Verkäuferangaben' });
    expect(within(checklist).getByText(/Telefonnummer: fehlt/)).toBeInTheDocument();
    expect(within(checklist).getByText(/nur falls eingetragen/)).toBeInTheDocument();
    expect(within(card).getByText(/Angaben zum Verkäufer unvollständig/)).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Freigeben' })).toBeDisabled();
  });
});

describe('promotions', () => {
  it('asks for the title only in the market language while merchant multilingual input is off', async () => {
    const ayran = { id: 'l1', name: 'Ayran', unitSize: '250 ml', price: { amountCents: 99, currency: 'EUR' }, available: true, maxOrderQuantity: 24, privateProduct: false };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/promotions': [],
      'GET /merchant/markets/m1/listings': { items: [ayran], page: 0, size: 50, totalItems: 1, totalPages: 1 },
      'GET /features': { SHOPPING_LIST: false, SHOPPING_LIST_VOICE: false, SHOPPING_LIST_MEDIA: false, MERCHANT_MULTILINGUAL: false },
      'POST /merchant/markets/m1/promotions': (call) => ({ id: 'pr2', ...(call.body as object) }),
    });
    await user.click(await screen.findByRole('button', { name: 'Aktionen' }));
    await user.type(await screen.findByLabelText('Titel'), 'Ayran-Woche');
    expect(screen.queryByLabelText('Titel · English')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Fehlende Sprachen übersetzen' })).toBeNull();
    await user.click(await screen.findByRole('checkbox', { name: 'Ayran' }));
    await user.click(screen.getByRole('button', { name: 'Aktion starten' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Aktion gestartet.');
    const body = calls.find((c) => c.method === 'POST' && c.path.endsWith('/promotions'))?.body as Record<string, unknown>;
    expect(body.title).toEqual({ de: 'Ayran-Woche' });
  });

  it('sets an offer price per product, on chosen weekdays and hours', async () => {
    const ayran = { id: 'l1', name: 'Ayran', unitSize: '250 ml', price: { amountCents: 500, currency: 'EUR' }, available: true, maxOrderQuantity: 24, privateProduct: false };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/promotions': [],
      'GET /merchant/markets/m1/listings': { items: [ayran], page: 0, size: 50, totalItems: 1, totalPages: 1 },
      'POST /merchant/markets/m1/promotions': (call) => ({ id: 'pr2', ...(call.body as object) }),
    });
    await user.click(await screen.findByRole('button', { name: 'Aktionen' }));
    await user.type(await screen.findByLabelText(/^Titel/), 'Frühstücksangebot');
    await user.selectOptions(screen.getByLabelText('Art'), 'FIXED_PRICE');
    await user.click(await screen.findByRole('checkbox', { name: 'Ayran' }));
    await user.type(screen.getByLabelText('Angebotspreis für Ayran (€)'), '5,00');
    // Only weekdays Monday to Friday, 07:00–10:00.
    const days = screen.getByRole('group', { name: 'Wochentage' });
    await user.click(within(days).getByRole('checkbox', { name: 'Sa' }));
    await user.click(within(days).getByRole('checkbox', { name: 'So' }));
    await user.click(screen.getByRole('checkbox', { name: 'Ganztägig' }));
    await user.clear(screen.getByLabelText('Täglich ab'));
    await user.type(screen.getByLabelText('Täglich ab'), '07:00');
    await user.clear(screen.getByLabelText('Täglich bis'));
    await user.type(screen.getByLabelText('Täglich bis'), '10:00');
    await user.click(screen.getByRole('button', { name: 'Aktion starten' }));
    // 5,00 € is not below the regular price.
    expect(await screen.findByText('Für jedes gewählte Produkt einen Angebotspreis unter dem normalen Preis eingeben.')).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Angebotspreis für Ayran (€)'));
    await user.type(screen.getByLabelText('Angebotspreis für Ayran (€)'), '2');
    await user.click(screen.getByRole('button', { name: 'Aktion starten' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Aktion gestartet.');
    const body = calls.find((c) => c.method === 'POST' && c.path.endsWith('/promotions'))?.body as Record<string, unknown>;
    expect(body).toEqual(
      expect.objectContaining({
        type: 'FIXED_PRICE',
        tiers: null,
        offers: [{ productId: 'l1', offerPrice: { amountCents: 200, currency: 'EUR' } }],
        weekdays: [1, 2, 3, 4, 5],
        dailyFrom: '07:00',
        dailyUntil: '10:00',
        productIds: ['l1'],
      }),
    );
    // A week from today, ending at midnight after the last day.
    expect(new Date(body.validUntil as string).getTime() - new Date(body.validFrom as string).getTime()).toBeGreaterThan(6 * 24 * 3600 * 1000);
  });

  it('translates the title, picks products and starts a promotion; ends a running one', async () => {
    const running = {
      id: 'pr1',
      title: 'Teewoche',
      type: 'PERCENT_OFF',
      percentOff: 25,
      validFrom: '2026-10-01T00:00:00Z',
      validUntil: '2999-01-01T00:00:00Z',
      productIds: ['l9'],
    };
    const ayran = { id: 'l1', name: 'Ayran', unitSize: '250 ml', price: { amountCents: 99, currency: 'EUR' }, available: true, maxOrderQuantity: 24, privateProduct: false };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/promotions': [running],
      'GET /merchant/markets/m1/listings': { items: [ayran], page: 0, size: 50, totalItems: 1, totalPages: 1 },
      'GET /features': { SHOPPING_LIST: false, SHOPPING_LIST_VOICE: false, SHOPPING_LIST_MEDIA: false, MERCHANT_MULTILINGUAL: true },
      'POST /merchant/translations': { fields: [{ key: 'text', values: { en: 'Ayran week', ar: 'أسبوع العيران' } }] },
      'POST /merchant/markets/m1/promotions': (call) => ({ ...running, id: 'pr2', ...(call.body as object) }),
      'DELETE /merchant/markets/m1/promotions/pr1': respond(204),
    });
    await user.click(await screen.findByRole('button', { name: 'Aktionen' }));
    expect(await screen.findByRole('rowheader', { name: 'Teewoche' })).toBeInTheDocument();

    // Validation first: no title.
    await user.click(screen.getByRole('button', { name: 'Aktion starten' }));
    expect(await screen.findByText('Bitte einen Titel in der Hauptsprache eingeben.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Titel · Deutsch (Hauptsprache)'), 'Ayran-Woche');
    await user.click(screen.getByRole('button', { name: 'Fehlende Sprachen übersetzen' }));
    await waitFor(() => expect(screen.getByLabelText('Titel · English')).toHaveValue('Ayran week'));
    expect(screen.getByLabelText('Titel · Deutsch (Hauptsprache)')).toHaveValue('Ayran-Woche');
    await user.selectOptions(screen.getByLabelText('Art'), 'MULTI_BUY');
    await user.type(screen.getByLabelText('Preis zusammen in € (Stufe 1)'), '2,49');
    await user.click(screen.getByRole('button', { name: 'Weitere Stufe' }));
    await user.type(screen.getByLabelText('Stück (Stufe 2)'), '7');
    await user.type(screen.getByLabelText('Preis zusammen in € (Stufe 2)'), '5');
    await user.click(await screen.findByRole('checkbox', { name: 'Ayran' }));
    await user.click(screen.getByRole('button', { name: 'Aktion starten' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Aktion gestartet.');
    const body = calls.find((c) => c.method === 'POST' && c.path.endsWith('/promotions'))?.body as Record<string, unknown>;
    expect(body).toEqual(
      expect.objectContaining({
        title: { de: 'Ayran-Woche', en: 'Ayran week', ar: 'أسبوع العيران' },
        type: 'MULTI_BUY',
        percentOff: null,
        tiers: [
          { quantity: 3, price: { amountCents: 249, currency: 'EUR' } },
          { quantity: 7, price: { amountCents: 500, currency: 'EUR' } },
        ],
        offers: null,
        weekdays: null,
        dailyFrom: null,
        productIds: ['l1'],
      }),
    );

    await user.click(screen.getByRole('button', { name: 'Beenden' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true));
  });
});

describe('admin catalog review', () => {
  const item = {
    id: 'p1',
    market: { id: 'm1', name: 'Anatolia Frischmarkt' },
    category: { id: 'c1', name: 'Feinkost' },
    brand: null,
    unitSize: '250 g',
    barcode: null,
    imageUrl: null,
    reviewStatus: 'PENDING',
    createdAt: '2026-10-21T10:00:00Z',
    texts: {
      de: { name: 'Quittengelee', description: null, machineTranslated: false },
      en: { name: 'Quince jam', description: null, machineTranslated: true },
    },
    duplicates: [{ id: 'p9', name: 'Quittengelee', unitSize: '250 g', barcode: '4000000000001' }],
  };

  it('corrects a machine translation, then promotes the product', async () => {
    let current: CatalogReviewItem = item as CatalogReviewItem;
    const { user, calls } = renderPortal(
      {
        'GET /admin/markets?status=PENDING_REVIEW': [],
        'GET /admin/catalog/products?status=PENDING&page=0&size=20': () => ({
          items: current.reviewStatus === 'PENDING' ? [current] : [],
          page: 0,
          size: 20,
          totalItems: current.reviewStatus === 'PENDING' ? 1 : 0,
          totalPages: 1,
        }),
        'PUT /admin/catalog/products/p1/texts': (call) => {
          const body = call.body as { names: Record<string, string> };
          current = { ...current, texts: { ...current.texts, en: { name: body.names.en ?? '', description: null, machineTranslated: false } } };
          return current;
        },
        'POST /admin/catalog/products/p1/review': (call) => {
          current = { ...current, reviewStatus: (call.body as { decision: string }).decision === 'PROMOTE' ? 'PROMOTED' : 'KEPT_PRIVATE', market: null };
          return current;
        },
      },
      { accessToken: 'a', refreshToken: 'r', user: adminUser },
    );
    await user.click(await screen.findByRole('button', { name: 'Katalogprüfung' }));
    const card = await screen.findByRole('article', { name: 'Quittengelee' });
    expect(within(card).getByText('maschinell')).toBeInTheDocument();
    expect(within(card).getByRole('note')).toHaveTextContent('Mögliche Dubletten');

    const english = within(card).getByLabelText('Name · English');
    await user.clear(english);
    await user.type(english, 'Quince jelly');
    // Unsaved texts block the decision.
    expect(within(card).getByRole('button', { name: 'In den gemeinsamen Katalog' })).toBeDisabled();
    await user.click(within(card).getByRole('button', { name: 'Texte speichern' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ names: { en: 'Quince jelly' }, descriptions: { en: '' } }));
    const saved = await screen.findByRole('article', { name: 'Quittengelee' });
    await waitFor(() => expect(within(saved).queryByText('maschinell')).toBeNull());

    await user.click(within(saved).getByRole('button', { name: 'In den gemeinsamen Katalog' }));
    expect(await screen.findByText('Keine Produkte mit diesem Status.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ decision: 'PROMOTE' });
  });
});

describe('market profile', () => {
  const profileMarket = () =>
    market({
      descriptions: { de: 'Frisch vom Großmarkt', ar: 'طازج' },
      cuisines: [],
      location: { lat: 53.5527, lng: 10.0144 },
      tags: [],
      fulfilment: { delivery: false, pickup: true, minimumOrderValue: { amountCents: 0, currency: 'EUR' } },
      openingHours: [{ dayOfWeek: 1, opensAt: '08:00', closesAt: '20:00' }],
    });
  const lookups = {
    'GET /market-types': [{ code: 'GROCERY', name: 'Lebensmittel' }],
    'GET /cuisines': [{ code: 'turkish', name: 'Türkisch' }],
  };

  it('tells the merchant which seller information approval still needs', async () => {
    const incomplete = {
      ...profileMarket(),
      status: 'PENDING_REVIEW',
      phone: null,
      review: { complete: false, items: [{ code: 'PHONE', ok: false, required: true }, { code: 'REGISTER_INFO', ok: false, required: false }] },
    };
    const { user } = renderPortal({ 'GET /merchant/markets': [incomplete], 'GET /merchant/markets/m1/orders': [], ...lookups });
    await user.click(await screen.findByRole('button', { name: 'Marktprofil' }));
    expect(await screen.findByRole('note')).toHaveTextContent('Für die Freigabe fehlen noch Angaben zum Verkäufer: Telefonnummer.');
  });

  it('saves the edited profile with a looked-up address and keeps every description language', async () => {
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [profileMarket()],
      'GET /merchant/markets/m1/orders': [],
      ...lookups,
      'GET /geo/search': [{ label: 'Lange Reihe 12, 20099 Hamburg (St. Georg)', city: 'Hamburg', location: { lat: 53.556, lng: 10.012 } }],
      'PUT /merchant/markets/m1': (call: { body: unknown }) => ({ ...profileMarket(), ...(call.body as object), descriptions: (call.body as { description: object }).description }),
    });
    await user.click(await screen.findByRole('button', { name: 'Marktprofil' }));
    expect(await screen.findByRole('heading', { name: 'Marktprofil', level: 1 })).toBeInTheDocument();

    const address = screen.getByLabelText('Adresse');
    await user.clear(address);
    await user.type(address, 'Lange Reihe');
    expect(screen.getByText(/Bitte die Adresse suchen/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Adresse suchen' }));
    await user.click(await screen.findByRole('button', { name: 'Lange Reihe 12, 20099 Hamburg (St. Georg)' }));
    await user.click(await screen.findByRole('checkbox', { name: 'Türkisch' }));
    await user.click(screen.getByRole('checkbox', { name: 'Lieferung' }));
    await user.type(screen.getByLabelText('Lieferradius (km)'), '3');
    await user.type(screen.getByLabelText('Liefergebühr (€)'), '2,90');
    await user.click(screen.getByRole('button', { name: /Zeitraum für Dienstag hinzufügen/ }));
    await user.click(screen.getByRole('button', { name: 'Profil speichern' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Profil gespeichert.');
    const body = calls.find((c) => c.method === 'PUT')?.body as Record<string, unknown>;
    expect(body).toMatchObject({
      addressLine: 'Lange Reihe 12, 20099 Hamburg (St. Georg)',
      location: { lat: 53.556, lng: 10.012 },
      cuisineCodes: ['turkish'],
      description: { de: 'Frisch vom Großmarkt', ar: 'طازج' },
      fulfilment: { delivery: true, deliveryRadiusKm: 3, deliveryFee: { amountCents: 290, currency: 'EUR' } },
      openingHours: [
        { dayOfWeek: 1, opensAt: '08:00', closesAt: '20:00' },
        { dayOfWeek: 2, opensAt: '09:00', closesAt: '18:00' },
      ],
    });
  });

  it('marks problems before sending and shows server field errors', async () => {
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [profileMarket()],
      'GET /merchant/markets/m1/orders': [],
      ...lookups,
      'PUT /merchant/markets/m1': respond(400, { status: 400, code: 'VALIDATION_FAILED', errors: [{ field: 'openingHours', code: 'Overlap', message: 'overlap' }] }),
    });
    await user.click(await screen.findByRole('button', { name: 'Marktprofil' }));
    await user.clear(await screen.findByLabelText('Name des Markts'));
    await user.click(screen.getByRole('button', { name: 'Profil speichern' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte 1 markierte Angabe(n) prüfen.');
    expect(screen.getByLabelText('Name des Markts')).toHaveAttribute('aria-invalid', 'true');
    expect(calls.some((c) => c.method === 'PUT')).toBe(false);

    await user.type(screen.getByLabelText('Name des Markts'), 'Anatolia');
    await user.click(screen.getByRole('button', { name: 'Profil speichern' }));
    expect(await screen.findByText('Zeiträume überschneiden sich.')).toBeInTheDocument();
  });

  it('shows server errors that belong to no field', async () => {
    const { user } = renderPortal({
      'GET /merchant/markets': [profileMarket()],
      'GET /merchant/markets/m1/orders': [],
      ...lookups,
      'PUT /merchant/markets/m1': respond(400, { status: 400, code: 'VALIDATION_FAILED', errors: [{ field: 'body', code: 'Unreadable', message: 'x' }] }),
    });
    await user.click(await screen.findByRole('button', { name: 'Marktprofil' }));
    await user.click(await screen.findByRole('button', { name: 'Profil speichern' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Bitte prüfe die Eingaben.');
    expect(screen.queryByText(/markierte Angabe/)).toBeNull();
  });
});

describe('insights', () => {
  const eur = (amountCents: number) => ({ amountCents, currency: 'EUR' });
  const insights = (days: number) => ({
    from: '2026-10-16',
    to: '2026-10-22',
    totals: { orders: 4, revenue: eur(5200), averageOrderValue: eur(1300), discounts: eur(150) },
    previous: { orders: 2, revenue: eur(4000), averageOrderValue: eur(2000), discounts: eur(0) },
    cancelledOrders: 1,
    rejectedOrders: 0,
    pickupOrders: 3,
    deliveryOrders: 1,
    customers: 3,
    returningCustomers: 1,
    daily: Array.from({ length: days }, (_, i) => ({ date: `2026-10-${String(16 + (i % 7)).padStart(2, '0')}`, orders: i === days - 1 ? 4 : 0, revenue: eur(i === days - 1 ? 5200 : 0) })),
    ordersByHour: Array.from({ length: 24 }, (_, h) => (h === 17 ? 3 : h === 9 ? 1 : 0)),
    topProducts: [{ listingId: 'l1', name: 'Ayran', unitSize: '0,25 l', quantity: 6, revenue: eur(900) }],
  });

  it('shows the headline numbers against the period before and switches the period', async () => {
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/insights': (call: { path: string }) => insights(Number(new URLSearchParams(call.path.split('?')[1]).get('days'))),
    });
    await user.click(await screen.findByRole('button', { name: 'Übersicht' }));
    expect((await screen.findAllByText('52,00 €')).length).toBeGreaterThan(0);
    expect(screen.getByText('30 % mehr als im Zeitraum davor')).toBeInTheDocument();
    expect(screen.getByText('100 % mehr als im Zeitraum davor')).toBeInTheDocument();
    expect(screen.getByText('35 % weniger als im Zeitraum davor')).toBeInTheDocument();
    expect(screen.getByText('davon 1 wiederkehrend (33 %)')).toBeInTheDocument();
    expect(screen.getByText(/3 Abholungen · 1 Lieferung · 1 storniert · 0 abgelehnt · 1,50 € Rabatte gewährt/)).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: /Ayran/ })).toBeInTheDocument();

    // The chart's values are reachable by keyboard and in the table view.
    const chart = screen.getByRole('img', { name: 'Umsatz pro Tag' });
    chart.focus();
    expect(await screen.findByRole('status')).toHaveTextContent('52 €');
    await user.keyboard('{ArrowLeft}');
    expect(screen.getByRole('status')).toHaveTextContent('0 €');

    await user.click(screen.getByRole('button', { name: '7 Tage' }));
    await waitFor(() => expect(calls.some((c) => c.path === '/merchant/markets/m1/insights?days=7')).toBe(true));
    expect(screen.getByRole('button', { name: '7 Tage' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('computes clean axis steps and changes', () => {
    expect(niceTicks(5200)).toEqual([0, 2000, 4000, 6000]);
    expect(niceTicks(3)).toEqual([0, 1, 2, 3]);
    expect(niceTicks(0)).toEqual([0, 1]);
    // Counts and cents never get fractional or repeated ticks.
    expect(niceTicks(1, 1)).toEqual([0, 1]);
    expect(niceTicks(150, 100)).toEqual([0, 100, 200]);
    expect(change(150, 100)).toBe(50);
    expect(change(5, 0)).toBeNull();
  });
});

describe('new order alerts', () => {
  it('names new orders, chimes, notifies and counts them in the tab title', async () => {
    const first = { ...order, id: 'o1', orderNumber: 'DK-1' };
    const second = { ...order, id: 'o2', orderNumber: 'DK-2', total: { amountCents: 1250, currency: 'EUR' } };
    let polls = 0;
    const sound = vi.fn();
    const notify = vi.fn();
    const backend = fakeBackend({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [first, second],
      'GET /merchant/markets/m1/orders?status=PLACED': () => (++polls === 1 ? [first] : [first, second]),
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <App
        dependencies={{
          fetch: backend.fetch,
          store: memoryStore(merchantSession),
          queryClient,
          alerts: { intervalMs: 50, enabled: true, signals: { sound, notify, prepare: vi.fn(() => true) } },
        }}
      />,
    );
    const toast = await screen.findByText('Neue Bestellung');
    expect(toast.closest('.toast')).toHaveTextContent('DK-2');
    expect(toast.closest('.toast')).not.toHaveTextContent('DK-1');
    expect(sound).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledWith('Neue Bestellung', expect.stringContaining('DK-2'));
    await waitFor(() => expect(document.title).toBe('(2) Dukkan Portal'));
    expect(screen.getByTestId('order-announcement')).toHaveTextContent('Neue Bestellung DK-2');
    // Further polls with the same orders stay quiet.
    await waitFor(() => expect(polls).toBeGreaterThan(3));
    expect(sound).toHaveBeenCalledTimes(1);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Ansehen' }));
    expect(screen.queryByText('Neue Bestellung')).toBeNull();
    expect(window.location.hash).toBe('#/orders');
  });

  it('only reports orders that were not there before', () => {
    const baseline = newArrivals(undefined, ['a', 'b']);
    expect(baseline.added).toEqual([]);
    expect(newArrivals(baseline.seen, ['b', 'c']).added).toEqual(['c']);
    expect(newArrivals(baseline.seen, ['a']).added).toEqual([]);
  });
});

describe('languages', () => {
  it('switches to Arabic right-to-left', async () => {
    const { user } = renderPortal({ 'GET /merchant/markets': [market()], 'GET /merchant/markets/m1/orders': [] });
    await user.selectOptions(await screen.findByLabelText('Sprache'), 'ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(await screen.findByRole('heading', { name: 'الطلبات' })).toBeInTheDocument();
    applyLanguage('de');
    expect(document.documentElement.dir).toBe('ltr');
  });

  it('parses German and English prices', () => {
    expect(parsePrice('4,49 €')).toBe(449);
    expect(parsePrice('4.5')).toBe(450);
    expect(parsePrice('0')).toBeNull();
    expect(parsePrice('vier')).toBeNull();
  });
});

describe('orders and delivery settings', () => {
  const stored = {
    ordersEnabled: false,
    pausedUntil: null,
    closedFrom: null,
    closedUntil: null,
    deliveryCapacity: null,
    pickupCapacity: null,
    deliveryLeadMinutes: null,
    pickupLeadMinutes: null,
    daysAhead: null,
    deliveryHours: [],
    autoAccept: false,
    paymentMethods: ['CASH'],
    defaultMaxOrderQuantity: null,
    defaults: { deliveryCapacity: 6, pickupCapacity: 10, deliveryLeadMinutes: 120, pickupLeadMinutes: 60, daysAhead: 7, maxOrderQuantity: 24 },
  };

  it('tells the merchant on the board that online orders are off and opens the settings', async () => {
    const { user } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/order-settings': stored,
    });
    expect(await screen.findByText(/Online-Bestellungen sind ausgeschaltet/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Einstellungen öffnen' }));
    expect(await screen.findByRole('heading', { name: 'Bestellungen & Lieferung', level: 1 })).toBeInTheDocument();
    expect(screen.getByLabelText('Abholungen pro Zeitfenster')).toHaveAttribute('placeholder', 'Standard: 10');
  });

  it('switches orders on with own capacity, holiday, delivery times, card payment and automatic acceptance', async () => {
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/order-settings': stored,
      'PUT /merchant/markets/m1/order-settings': (call: { body: unknown }) => ({ ...(call.body as object), defaults: stored.defaults }),
    });
    await user.click(await screen.findByRole('button', { name: 'Bestellungen & Lieferung' }));
    await user.click(await screen.findByRole('checkbox', { name: 'Kunden können online bestellen' }));
    await user.type(screen.getByLabelText('Abholungen pro Zeitfenster'), '4');
    await user.type(screen.getByLabelText('Vorlauf Lieferung (Minuten)'), '9999');
    await user.click(screen.getByRole('checkbox', { name: 'Geschlossen in einem Zeitraum' }));
    await user.type(screen.getByLabelText('Von'), '2026-12-24');
    await user.click(screen.getByRole('checkbox', { name: 'Andere Lieferzeiten als die Öffnungszeiten' }));
    await user.click(screen.getByRole('checkbox', { name: 'Karte' }));
    await user.click(screen.getByRole('checkbox', { name: 'Bestellungen automatisch annehmen' }));
    await user.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    // Checked before sending: lead time out of range, holiday without end, no delivery times.
    expect(screen.getByText('Wert außerhalb des erlaubten Bereichs.')).toBeInTheDocument();
    expect(screen.getByText('Bitte Anfang und Ende angeben.')).toBeInTheDocument();
    expect(screen.getByText(/Mindestens eine Lieferzeit/)).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'PUT')).toBe(false);

    await user.clear(screen.getByLabelText('Vorlauf Lieferung (Minuten)'));
    await user.type(screen.getByLabelText('Bis einschließlich'), '2026-12-26');
    await user.click(screen.getByRole('button', { name: /Zeitraum für Dienstag hinzufügen/ }));
    await user.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Einstellungen gespeichert.');
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
      ordersEnabled: true,
      pausedUntil: null,
      closedFrom: '2026-12-24',
      closedUntil: '2026-12-26',
      deliveryCapacity: null,
      pickupCapacity: 4,
      deliveryLeadMinutes: null,
      pickupLeadMinutes: null,
      daysAhead: null,
      deliveryHours: [{ dayOfWeek: 2, opensAt: '09:00', closesAt: '18:00' }],
      autoAccept: true,
      paymentMethods: ['CASH', 'CARD'],
      defaultMaxOrderQuantity: null,
    });
  });

  it('pauses at once and ends the pause', async () => {
    let current: Record<string, unknown> = { ...stored, ordersEnabled: true };
    const { user, calls } = renderPortal({
      'GET /merchant/markets': [market()],
      'GET /merchant/markets/m1/orders': [],
      'GET /merchant/markets/m1/order-settings': () => current,
      'PUT /merchant/markets/m1/order-settings': (call: { body: unknown }) => {
        current = { ...(call.body as object), defaults: stored.defaults };
        return current;
      },
    });
    await user.click(await screen.findByRole('button', { name: 'Bestellungen & Lieferung' }));
    await user.click(await screen.findByRole('button', { name: '1 Stunde' }));
    expect(await screen.findByText(/Pausiert bis/)).toBeInTheDocument();
    const pausedUntil = (calls.find((c) => c.method === 'PUT')?.body as { pausedUntil: string; ordersEnabled: boolean }).pausedUntil;
    expect(new Date(pausedUntil).getTime()).toBeGreaterThan(Date.now() + 50 * 60 * 1000);
    await user.click(screen.getByRole('button', { name: 'Pause beenden' }));
    expect(await screen.findByRole('button', { name: '1 Stunde' })).toBeInTheDocument();
    expect((calls.filter((c) => c.method === 'PUT')[1]?.body as { pausedUntil: unknown }).pausedUntil).toBeNull();
  });
});

describe('admin features', () => {
  it('switches the shopping list and its parts on for all customers', async () => {
    let current: Record<string, boolean> = { SHOPPING_LIST: false, SHOPPING_LIST_VOICE: false, SHOPPING_LIST_MEDIA: false, MERCHANT_MULTILINGUAL: false };
    const { user, calls } = renderPortal(
      {
        'GET /admin/markets?status=PENDING_REVIEW': [],
        'GET /admin/features': () => current,
        'PUT /admin/features/SHOPPING_LIST': (call: { body: unknown }) => {
          current = { ...current, SHOPPING_LIST: (call.body as { enabled: boolean }).enabled };
          return current;
        },
      },
      { accessToken: 'a', refreshToken: 'r', user: adminUser },
    );
    await user.click(await screen.findByRole('button', { name: 'Funktionen' }));
    const list = await screen.findByRole('checkbox', { name: 'Einkaufsliste (eintippen)' });
    expect(list).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Einkaufsliste sprechen' })).not.toBeChecked();
    expect(screen.getAllByText(/Wirkt erst, wenn die Einkaufsliste an ist/)).toHaveLength(2);
    expect(screen.getByRole('checkbox', { name: 'Eingaben in mehreren Sprachen' })).not.toBeChecked();

    await user.click(list);
    expect(await screen.findByRole('checkbox', { name: 'Einkaufsliste (eintippen)' })).toBeChecked();
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ enabled: true });
    expect(screen.queryByText(/Wirkt erst, wenn die Einkaufsliste an ist/)).toBeNull();
  });
});
