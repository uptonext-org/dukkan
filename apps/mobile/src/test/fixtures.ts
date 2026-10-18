import type { Cart, CartItem, DeliverySlot, Money, ProductSummary } from '../shared/api';

export const eur = (amountCents: number): Money => ({ amountCents, currency: 'EUR' });

export function product(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    sku: 'DK-0001',
    name: 'Oat Drink Barista',
    brand: 'Feldgut',
    unitSize: '1 l',
    imageUrl: null,
    price: eur(249),
    comparisonPrice: { price: eur(249), unit: 'l' },
    promotion: null,
    available: true,
    ...overrides,
  };
}

export function cartItem(overrides: Partial<CartItem> = {}): CartItem {
  return {
    productId: '00000000-0000-4000-8000-000000000001',
    name: 'Oat Drink Barista',
    unitSize: '1 l',
    imageUrl: null,
    unitPrice: eur(249),
    quantity: 2,
    lineSubtotal: eur(498),
    lineDiscount: eur(0),
    lineTotal: eur(498),
    promotion: null,
    ...overrides,
  };
}

export function cart(overrides: Partial<Cart> = {}): Cart {
  return {
    id: '00000000-0000-4000-8000-0000000000c1',
    items: [cartItem()],
    itemCount: 2,
    subtotal: eur(498),
    discountTotal: eur(0),
    deliveryFee: eur(0),
    total: eur(498),
    minimumOrderValue: eur(3500),
    meetsMinimumOrderValue: false,
    delivery: null,
    ...overrides,
  };
}

export function slot(
  id: string,
  startsAt: string,
  endsAt: string,
  overrides: Partial<DeliverySlot> = {},
): DeliverySlot {
  return {
    id,
    startsAt,
    endsAt,
    orderCutoffAt: '2026-10-18T22:00:00+02:00',
    fee: eur(99),
    available: true,
    remainingCapacity: 5,
    ...overrides,
  };
}
