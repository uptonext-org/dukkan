import { screen, userEvent, within } from '@testing-library/react-native';

import { createFakeApiClient } from '../../../test/fakeApiClient';
import { cart, cartItem, eur, slot } from '../../../test/fixtures';
import { createTestDependencies, renderApp } from '../../../test/render';
import { minimumOrderProgress } from '../model';

const normalize = (s: string) => s.replace(/\u00a0/g, ' ');
const emptyPage = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };

function setup(cartResponse: ReturnType<typeof cart>, extra: Parameters<typeof createFakeApiClient>[0] = {}) {
  const fake = createFakeApiClient({
    ...extra,
    'GET /categories': [],
    'GET /products': emptyPage,
    'GET /cart': cartResponse,
    'GET /me': { id: 'u1', email: 'a@b.de', firstName: 'Ada', lastName: 'L' },
  });
  return { fake, dependencies: createTestDependencies(fake.client, 'token-1') };
}

async function openCart() {
  const user = userEvent.setup();
  await user.press(await screen.findByRole('button', { name: /^Cart/ }));
  return user;
}

describe('<CartScreen />', () => {
  it('renders server totals and the minimum-order message', async () => {
    const { dependencies } = setup(
      cart({
        items: [
          cartItem(),
          cartItem({
            productId: 'p2',
            name: 'Rye Bread',
            quantity: 3,
            unitPrice: eur(300),
            lineSubtotal: eur(900),
            lineDiscount: eur(180),
            lineTotal: eur(720),
            promotion: { promotionId: 'promo', label: '-20%', type: 'PERCENT_OFF' },
          }),
        ],
        itemCount: 5,
        subtotal: eur(1398),
        discountTotal: eur(180),
        deliveryFee: eur(199),
        total: eur(1417),
      }),
    );
    await renderApp(dependencies);
    await openCart();

    expect(await screen.findByText('5 items')).toBeOnTheScreen();
    expect(screen.getByLabelText('Subtotal: 13,98 Euro')).toBeOnTheScreen();
    expect(screen.getByLabelText('Discount: minus 1,80 Euro')).toBeOnTheScreen();
    expect(screen.getByLabelText('Delivery fee: 1,99 Euro')).toBeOnTheScreen();
    expect(screen.getByLabelText('Total: 14,17 Euro')).toBeOnTheScreen();

    // 35,00 € minimum - (13,98 € - 1,80 €) goods value = 22,82 € missing.
    const notice = screen.getByLabelText(
      'Minimum order value is 35,00 Euro. Add 22,82 Euro more to check out.',
    );
    expect(within(notice).getByText(/Minimum order value/)).toHaveTextContent(
      'Minimum order value is 35,00 €. Add 22,82 € more to check out.',
      { normalizer: normalize },
    );
    expect(screen.getByRole('button', { name: 'Place order' })).toBeDisabled();
    expect(screen.getByText('No delivery slot reserved yet.')).toBeOnTheScreen();
  });

  it('enables checkout once the minimum is met and a slot is reserved', async () => {
    const reserved = slot('s1', '2026-10-20T10:00:00+02:00', '2026-10-20T11:00:00+02:00');
    const { dependencies } = setup(
      cart({
        subtotal: eur(4000),
        total: eur(4099),
        deliveryFee: eur(99),
        meetsMinimumOrderValue: true,
        delivery: { slot: reserved, addressId: 'a1', expiresAt: '2026-10-19T22:00:00+02:00' },
      }),
    );
    await renderApp(dependencies);
    await openCart();

    expect(await screen.findByRole('button', { name: 'Place order' })).toBeEnabled();
    expect(screen.queryByText(/Minimum order value is/)).not.toBeOnTheScreen();
    expect(screen.getByText('Dienstag, 20. Oktober, 10:00–11:00')).toBeOnTheScreen();
  });

  it('shows the empty state', async () => {
    const { dependencies } = setup(cart({ items: [], itemCount: 0, subtotal: eur(0), total: eur(0) }));
    await renderApp(dependencies);
    await openCart();
    expect(await screen.findByText('Your cart is empty')).toBeOnTheScreen();
  });

  it('sends quantity changes and renders the returned cart', async () => {
    const updated = cart({
      items: [cartItem({ quantity: 3, lineTotal: eur(747), lineSubtotal: eur(747) })],
      itemCount: 3,
      subtotal: eur(747),
      total: eur(747),
    });
    const { fake, dependencies } = setup(cart(), { [`PUT /cart/items/${cartItem().productId}`]: updated });

    await renderApp(dependencies);
    const user = await openCart();
    await user.press(await screen.findByRole('button', { name: 'Increase quantity of Oat Drink Barista' }));

    expect(await screen.findByText('3 items')).toBeOnTheScreen();
    expect(screen.getByLabelText('Total: 7,47 Euro')).toBeOnTheScreen();
    expect(fake.calls).toContainEqual(expect.objectContaining({ method: 'PUT', body: { quantity: 3 } }));
  });
});

describe('minimumOrderProgress', () => {
  it('reports the remaining amount after discounts, excluding delivery', () => {
    const progress = minimumOrderProgress(
      cart({ subtotal: eur(2000), discountTotal: eur(250), deliveryFee: eur(199) }),
    );
    expect(progress.remaining.amountCents).toBe(1750);
    expect(progress.ratio).toBeCloseTo(0.5);
  });
});
