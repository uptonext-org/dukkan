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
});

describe('promotions', () => {
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
    await user.type(screen.getByLabelText('Preis zusammen (€)'), '2,49');
    await user.click(await screen.findByRole('checkbox', { name: 'Ayran' }));
    await user.click(screen.getByRole('button', { name: 'Aktion starten' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Aktion gestartet.');
    const body = calls.find((c) => c.method === 'POST' && c.path.endsWith('/promotions'))?.body as Record<string, unknown>;
    expect(body).toEqual(
      expect.objectContaining({
        title: { de: 'Ayran-Woche', en: 'Ayran week', ar: 'أسبوع العيران' },
        type: 'MULTI_BUY',
        percentOff: null,
        bundleQuantity: 3,
        bundlePrice: { amountCents: 249, currency: 'EUR' },
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
