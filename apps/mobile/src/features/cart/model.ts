import type { Cart, Money } from '../../shared/api';

export interface MinimumOrderProgress {
  /** 0..1, for the progress bar. */
  ratio: number;
  /** Amount still missing, display only (the server decides `meetsMinimumOrderValue`). */
  remaining: Money;
}

/**
 * Minimum order value applies to goods after discounts, excluding delivery (contract README).
 * Eligibility itself always comes from `cart.meetsMinimumOrderValue`.
 */
export function minimumOrderProgress(cart: Cart): MinimumOrderProgress {
  const goodsValue = cart.subtotal.amountCents - cart.discountTotal.amountCents;
  const threshold = cart.minimumOrderValue.amountCents;
  const ratio = threshold <= 0 ? 1 : Math.min(1, Math.max(0, goodsValue / threshold));
  return {
    ratio: cart.meetsMinimumOrderValue ? 1 : ratio,
    remaining: {
      amountCents: cart.meetsMinimumOrderValue ? 0 : Math.max(0, threshold - goodsValue),
      currency: cart.minimumOrderValue.currency,
    },
  };
}

export type CartCta =
  | { kind: 'chooseSlot'; fulfilment: 'DELIVERY' | 'PICKUP' }
  | { kind: 'order'; total: Money; enabled: boolean };

/**
 * Sticky CTA: "Lieferzeit/Abholzeit wählen" until a slot is reserved, then "Bestellen · 23,40 €",
 * enabled only when the server says the minimum order value is met.
 */
export function cartCta(cart: Cart): CartCta {
  if (cart.fulfilment == null) {
    return { kind: 'chooseSlot', fulfilment: cart.market.fulfilment.delivery ? 'DELIVERY' : 'PICKUP' };
  }
  return { kind: 'order', total: cart.total, enabled: cart.meetsMinimumOrderValue && cart.items.length > 0 };
}
