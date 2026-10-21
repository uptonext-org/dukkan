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

export type FeeLine =
  /** No slot reserved yet: the market's delivery fee (null = none) and its free-delivery threshold. */
  | { kind: 'deliveryEstimate'; fee: Money | null; freeFrom: Money | null }
  /** No slot reserved yet and the market offers pickup: pickup is always free (contract). */
  | { kind: 'pickupEstimate' }
  /** Reserved: the server's `deliveryFee` for this cart. */
  | { kind: 'delivery'; fee: Money }
  | { kind: 'pickup'; fee: Money };

/**
 * The fee rows of the cart summary. Before a reservation the server's `deliveryFee` is not
 * meaningful yet, so the rows describe what the market charges; afterwards they show the
 * server's amount for the reserved type.
 */
export function feeLines(cart: Cart): FeeLine[] {
  const reservation = cart.fulfilment ?? null;
  if (reservation !== null) {
    return [reservation.type === 'PICKUP' ? { kind: 'pickup', fee: cart.deliveryFee } : { kind: 'delivery', fee: cart.deliveryFee }];
  }
  const options = cart.market.fulfilment;
  const lines: FeeLine[] = [];
  if (options.delivery) {
    lines.push({ kind: 'deliveryEstimate', fee: options.deliveryFee ?? null, freeFrom: options.freeDeliveryFrom ?? null });
  }
  if (options.pickup) {
    lines.push({ kind: 'pickupEstimate' });
  }
  return lines;
}
