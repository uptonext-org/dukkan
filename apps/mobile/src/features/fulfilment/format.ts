import type { TFunction } from 'i18next';

import type { FulfilmentReservation } from '../../shared/api';
import { formatShortDate, formatTimeRange, isoWeekday } from '../../shared/format';

/** "Mi 21.10. · 08:00–09:00". */
export function reservationLabel(t: TFunction, reservation: Pick<FulfilmentReservation, 'slot'>): string {
  const { slot } = reservation;
  return `${t(`weekday.${isoWeekday(slot.startsAt)}`)} ${formatShortDate(slot.startsAt)} · ${formatTimeRange(slot.startsAt, slot.endsAt)}`;
}
