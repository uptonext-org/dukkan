import type { TFunction } from 'i18next';

import type { Order } from '../../shared/api';
import { formatShortDate, formatTimeRange, isoWeekday } from '../../shared/format';

/** "Lieferung · Mi 21.10. · 08:00–09:00". */
export function orderSlotLabel(t: TFunction, order: Pick<Order, 'slot' | 'fulfilmentType'>): string {
  const type = order.fulfilmentType === 'DELIVERY' ? t('fulfilment.delivery') : t('fulfilment.pickup');
  return `${type} · ${t(`weekday.${isoWeekday(order.slot.startsAt)}`)} ${formatShortDate(order.slot.startsAt)} · ${formatTimeRange(order.slot.startsAt, order.slot.endsAt)}`;
}
