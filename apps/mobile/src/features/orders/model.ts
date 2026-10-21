import type { FulfilmentType, Order, OrderStatus } from '../../shared/api';

/** Steps shown in the customer timeline for each fulfilment type (simulated in phase 1). */
export function timelineSteps(type: FulfilmentType): OrderStatus[] {
  return type === 'DELIVERY'
    ? ['PLACED', 'ACCEPTED', 'OUT_FOR_DELIVERY', 'COMPLETED']
    : ['PLACED', 'ACCEPTED', 'READY_FOR_PICKUP', 'COMPLETED'];
}

export type StepState = 'done' | 'current' | 'upcoming';

export function stepState(order: Pick<Order, 'status' | 'fulfilmentType'>, step: OrderStatus): StepState {
  const steps = timelineSteps(order.fulfilmentType);
  const current = steps.indexOf(order.status);
  const index = steps.indexOf(step);
  if (current < 0) {
    return 'upcoming';
  }
  if (index < current || (index === current && order.status === 'COMPLETED')) {
    return 'done';
  }
  return index === current ? 'current' : 'upcoming';
}

export function isTerminalFailure(status: OrderStatus): boolean {
  return status === 'REJECTED' || status === 'CANCELLED';
}

/** Client hint only; the server decides (ORDER_NOT_CANCELLABLE). */
export function canCancel(order: Order, now: Date = new Date()): boolean {
  return (order.status === 'PLACED' || order.status === 'ACCEPTED') && now.getTime() < Date.parse(order.slot.orderCutoffAt);
}

/**
 * Merchant status actions per the contract README:
 * PLACED → ACCEPTED | REJECTED; ACCEPTED → READY_FOR_PICKUP (pickup) | OUT_FOR_DELIVERY (delivery);
 * READY_FOR_PICKUP | OUT_FOR_DELIVERY → COMPLETED.
 */
export function nextMerchantStatuses(order: Pick<Order, 'status' | 'fulfilmentType'>): OrderStatus[] {
  switch (order.status) {
    case 'PLACED':
      return ['ACCEPTED', 'REJECTED'];
    case 'ACCEPTED':
      return [order.fulfilmentType === 'PICKUP' ? 'READY_FOR_PICKUP' : 'OUT_FOR_DELIVERY'];
    case 'READY_FOR_PICKUP':
    case 'OUT_FOR_DELIVERY':
      return ['COMPLETED'];
    default:
      return [];
  }
}
