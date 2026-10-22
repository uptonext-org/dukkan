import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { App } from '../App';
import type { Session } from '../api/client';
import type { CatalogReviewItem } from '../api/types';
import { applyLanguage } from '../i18n';
import { CSV_TEMPLATE } from '../pages/ImportPage';
import { nextStatuses } from '../pages/OrdersBoard';
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
  items: [{ productId: 'p1', name: 'Ayran', quantity: 2, lineTotal: { amountCents: 198, currency: 'EUR' } }],
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
