import type { TFunction } from 'i18next';

import type { FulfilmentReservation } from '../../shared/api';
import { formatShortDate, formatTimeRange, isolate, isoWeekday } from '../../shared/format';

/** "Mi 21.10. · 08:00–09:00" (date and time isolated so they keep their order in Arabic). */
export function reservationLabel(t: TFunction, reservation: Pick<FulfilmentReservation, 'slot'>): string {
  const { slot } = reservation;
  return `${t(`weekday.${isoWeekday(slot.startsAt)}`)} ${isolate(formatShortDate(slot.startsAt))} · ${isolate(formatTimeRange(slot.startsAt, slot.endsAt))}`;
}
