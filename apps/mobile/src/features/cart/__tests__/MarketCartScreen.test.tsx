import { screen, userEvent, waitFor, within } from '@testing-library/react-native';

import type { Cart } from '../../../shared/api';
import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart, cartItem, eur, market, MARKET_ID, marketHome, order, page, slot } from '../../../test/fixtures';
import { createTestDependencies, normalize, renderApp } from '../../../test/render';
import { cartCta, minimumOrderProgress } from '../model';

const address = {
  id: '00000000-0000-4000-8000-0000000000ad',
  label: 'Zuhause',
  street: 'Lange Reihe',
  houseNumber: '12',
  postalCode: '20099',
  city: 'Hamburg',
  countryCode: 'DE' as const,
  isDefault: true,
};

const reservedSlot = slot('00000000-0000-4000-8000-0000000000s1', '2026-10-21T08:00:00+02:00', '2026-10-21T09:00:00+02:00');

function setup(cartResponse: Cart, extra: Parameters<typeof createFakeApiClient>[0] = {}) {
  const fake = createFakeApiClient({
    'GET /market-types': [],
    'GET /cuisines': [],
    'GET /markets': page([cartResponse.market]),
    [`GET /markets/${MARKET_ID}`]: cartResponse.market,
    [`GET /markets/${MARKET_ID}/home`]: marketHome({ market: cartResponse.market }),
    'GET /favorites/markets': [],
    'GET /carts': [{ cartId: cartResponse.id, market: cartResponse.market, itemCount: cartResponse.itemCount, total: cartResponse.total, updatedAt: '2026-10-20T18:00:00+02:00' }],
    'GET /me': { id: 'u1', email: 'ada@example.de', firstName: 'Ada', lastName: 'L', roles: ['CUSTOMER'] },
    'GET /addresses': [address],
    [`GET /markets/${MARKET_ID}/cart`]: cartResponse,
    ...extra,
  });
  return { fake, dependencies: createTestDependencies(fake.client, { token: 'jwt-1' }) };
}

async function openCart() {
  const user = userEvent.setup();
  await user.press(await screen.findByTestId(`market-card-${MARKET_ID}`));
  // Badge on the market's cart tab: the total, spoken in euros.
  const tab = await screen.findByLabelText('Warenkorb, 10,79 Euro');
  expect(within(tab).getByText('10,79')).toBeOnTheScreen();
  await user.press(tab);
  await screen.findByTestId('market-cart');
  return user;
}

describe('cart model', () => {
  it('computes the minimum-order progress after discounts, excluding delivery', () => {
    const progress = minimumOrderProgress(cart({ subtotal: eur(1156), discountTotal: eur(77), deliveryFee: eur(299) }));
    expect(progress.remaining.amountCents).toBe(421);
    expect(progress.ratio).toBeCloseTo(1079 / 1500);
  });

  it('asks for a slot first, then offers ordering when the server says the minimum is met', () => {
    expect(cartCta(cart())).toEqual({ kind: 'chooseSlot', fulfilment: 'DELIVERY' });
    expect(cartCta(cart({ market: market({ fulfilment: { ...market().fulfilment, delivery: false } }) }))).toEqual({ kind: 'chooseSlot', fulfilment: 'PICKUP' });
    const reserved = cart({ fulfilment: { type: 'DELIVERY', slot: reservedSlot, addressId: address.id, expiresAt: reservedSlot.orderCutoffAt } });
    expect(cartCta(reserved)).toEqual({ kind: 'order', total: eur(1079), enabled: false });
    expect(cartCta({ ...reserved, meetsMinimumOrderValue: true, total: eur(2340) })).toEqual({ kind: 'order', total: eur(2340), enabled: true });
  });
});

describe('Market cart', () => {
  it('shows rows with quantity pill, discount label and superscript prices, the summary and the "choose time" CTA', async () => {
    const { dependencies } = setup(cart());
    await renderApp(dependencies);
    await openCart();

    const row = screen.getByTestId(`cart-row-${cartItem().productId}`);
    expect(within(row).getByRole('button', { name: '2 × Salatgurke' })).toBeOnTheScreen();
    expect(within(row).getByText('10 % Rabatt')).toBeOnTheScreen();
    expect(within(row).getByLabelText('Regulär 2,38 Euro')).toBeOnTheScreen();
    expect(within(row).getByLabelText('Jetzt 2,14 Euro')).toBeOnTheScreen();

    const summary = screen.getByTestId('cart-summary');
    expect(within(summary).getByLabelText('Rabatt: minus 0,77 Euro')).toBeOnTheScreen();
    expect(within(summary).getByLabelText('Lieferung: Gratis')).toBeOnTheScreen();
    expect(within(summary).getByLabelText('Total: 10,79 Euro')).toBeOnTheScreen();
    expect(within(summary).getByText('Noch 4,21 € bis zum Mindestbestellwert von 15,00 €.', { normalizer: normalize })).toBeOnTheScreen();
    expect(within(summary).getByRole('progressbar')).toBeOnTheScreen();

    expect(screen.getByTestId('cart-cta')).toHaveAccessibleName('Lieferzeit wählen');
    expect(screen.getByTestId('fulfilment-bar')).toHaveAccessibleName('Lieferzeit wählen');
  });

  it('reserves a slot in the fulfilment sheet, then the CTA becomes "Bestellen · 23,40 €" and places the order', async () => {
    const reserved = cart({
      subtotal: eur(2417),
      discountTotal: eur(77),
      total: eur(2340),
      meetsMinimumOrderValue: true,
      fulfilment: { type: 'DELIVERY', slot: reservedSlot, addressId: address.id, expiresAt: reservedSlot.orderCutoffAt },
    });
    const { fake, dependencies } = setup(cart(), {
      [`GET /markets/${MARKET_ID}/slots`]: [
        reservedSlot,
        slot('00000000-0000-4000-8000-0000000000s2', '2026-10-21T09:00:00+02:00', '2026-10-21T10:00:00+02:00', { available: false, remainingCapacity: 0 }),
      ],
      [`PUT /markets/${MARKET_ID}/cart/fulfilment`]: reserved,
      [`POST /markets/${MARKET_ID}/orders`]: order({ total: eur(2340) }),
      [`GET /orders/${order().id}`]: order({ total: eur(2340) }),
    });
    await renderApp(dependencies);
    const user = await openCart();

    await user.press(screen.getByTestId('cart-cta'));
    const sheet = await screen.findByTestId('fulfilment-sheet');
    // Market offers both: segmented Lieferung | Abholung.
    expect(within(sheet).getByRole('tab', { name: 'Lieferung' })).toBeSelected();
    expect(within(sheet).getByRole('tab', { name: 'Abholung' })).toBeOnTheScreen();
    expect(within(sheet).getByRole('button', { name: /Zuhause/ })).toBeSelected();
    expect(within(sheet).getByRole('button', { name: 'Mi 21.10.' })).toBeSelected();
    // Unavailable window is disabled and announced.
    expect(within(sheet).getByRole('button', { name: '09:00–10:00, Gratis, nicht verfügbar' })).toBeDisabled();

    await user.press(within(sheet).getByRole('button', { name: '08:00–09:00, Gratis, noch 4 frei' }));
    await waitFor(() =>
      expect(fake.calls).toContainEqual(
        expect.objectContaining({ method: 'PUT', path: `/markets/${MARKET_ID}/cart/fulfilment`, body: { type: 'DELIVERY', slotId: reservedSlot.id, addressId: address.id } }),
      ),
    );
    expect(fake.calls).toContainEqual(expect.objectContaining({ path: `/markets/${MARKET_ID}/slots`, query: { type: 'DELIVERY', addressId: address.id } }));

    const cta = await screen.findByTestId('cart-cta');
    await waitFor(() => expect(normalize(cta.props.accessibilityLabel as string)).toBe('Bestellen · 23,40 €'));
    expect(screen.getByTestId('fulfilment-bar')).toHaveAccessibleName('Mi 21.10. · 08:00–09:00');

    await user.press(cta);
    expect(await screen.findByTestId('order-confirmation')).toHaveTextContent(/DK-20261020-000042/);
    const placed = fake.calls.find((c) => c.method === 'POST' && c.path === `/markets/${MARKET_ID}/orders`);
    expect(placed?.headers?.['Idempotency-Key']).toEqual(expect.any(String));
  });

  it('keeps ordering disabled until the minimum order value is met', async () => {
    const { dependencies } = setup(
      cart({ fulfilment: { type: 'PICKUP', slot: { ...reservedSlot, type: 'PICKUP' }, addressId: null, expiresAt: reservedSlot.orderCutoffAt } }),
    );
    await renderApp(dependencies);
    await openCart();
    const cta = screen.getByTestId('cart-cta');
    expect(normalize(cta.props.accessibilityLabel as string)).toBe('Bestellen · 10,79 €');
    expect(cta).toBeDisabled();
    expect(within(screen.getByTestId('cart-summary')).getByLabelText('Abholung: Gratis')).toBeOnTheScreen();
  });

  it('offers only the fulfilment types the market has (pickup-only market)', async () => {
    const pickupOnly = market({ fulfilment: { delivery: false, pickup: true, minimumOrderValue: eur(0) } });
    const { fake, dependencies } = setup(cart({ market: pickupOnly }), {
      [`GET /markets/${MARKET_ID}/slots`]: [slot('p1', '2026-10-21T10:00:00+02:00', '2026-10-21T11:00:00+02:00', { type: 'PICKUP' })],
    });
    await renderApp(dependencies);
    const user = await openCart();
    expect(screen.getByTestId('cart-cta')).toHaveAccessibleName('Abholzeit wählen');
    await user.press(screen.getByTestId('cart-cta'));
    const sheet = await screen.findByTestId('fulfilment-sheet');
    expect(within(sheet).queryByRole('tab')).not.toBeOnTheScreen();
    expect(within(sheet).getByTestId('fulfilment-single-type')).toHaveTextContent('Dieser Markt bietet nur Abholung');
    expect(within(sheet).queryByText('Lieferadresse')).not.toBeOnTheScreen();
    await waitFor(() => expect(fake.calls).toContainEqual(expect.objectContaining({ path: `/markets/${MARKET_ID}/slots`, query: { type: 'PICKUP', addressId: undefined } })));
  });
});
