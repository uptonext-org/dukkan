import type { Order, OrderStatus } from '../../shared/api';
import type { BadgeTone } from '../../shared/ui';

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: 'Placed',
  CANCELLED: 'Cancelled',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
};

export const ORDER_STATUS_TONE: Record<OrderStatus, BadgeTone> = {
  PLACED: 'success',
  CANCELLED: 'neutral',
  OUT_FOR_DELIVERY: 'warning',
  DELIVERED: 'success',
};

/** Client-side hint only; the server decides (ORDER_NOT_CANCELLABLE). */
export function canCancel(order: Order, now: Date = new Date()): boolean {
  return order.status === 'PLACED' && now.getTime() < Date.parse(order.slot.orderCutoffAt);
}
