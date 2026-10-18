import type { Promotion } from '../../shared/api';
import { formatMoney } from '../../shared/format/money';

/** Human-readable mechanic, built only from server-provided promotion fields. */
export function describePromotion(promotion: Promotion): string {
  if (promotion.type === 'PERCENT_OFF' && promotion.percentOff != null) {
    return `${promotion.percentOff}% off`;
  }
  if (promotion.type === 'MULTI_BUY' && promotion.bundleQuantity != null && promotion.bundlePrice != null) {
    return `${promotion.bundleQuantity} for ${formatMoney(promotion.bundlePrice)}`;
  }
  return 'Special offer';
}
