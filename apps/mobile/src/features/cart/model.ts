import type { Cart, Money } from '../../shared/api';

export interface MinimumOrderProgress {
  /** 0..1, for the progress bar. */
  ratio: number;
  /** Amount still missing, for display only (server decides `meetsMinimumOrderValue`). */
  remaining: Money;
}

/**
 * Display helper for the minimum-order hint. The contract defines the threshold against
 * the goods value after discounts (subtotal - discountTotal), excluding the delivery fee.
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
