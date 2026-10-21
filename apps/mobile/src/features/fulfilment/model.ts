import type { FulfilmentOptions, FulfilmentType, Slot } from '../../shared/api';
import { dayKey, formatShortDate, isoWeekday } from '../../shared/format';

export interface SlotDay {
  key: string;
  weekday: 1 | 2 | 3 | 4 | 5 | 6 | 7;
  date: string;
  slots: Slot[];
  hasAvailability: boolean;
}

/** Groups slots by calendar day in Europe/Berlin, in start-time order. */
export function groupSlotsByDay(slots: readonly Slot[]): SlotDay[] {
  const days = new Map<string, SlotDay>();
  const sorted = [...slots].sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt));
  for (const slot of sorted) {
    const key = dayKey(slot.startsAt);
    let day = days.get(key);
    if (!day) {
      day = { key, weekday: isoWeekday(slot.startsAt), date: formatShortDate(slot.startsAt), slots: [], hasAvailability: false };
      days.set(key, day);
    }
    day.slots.push(slot);
    day.hasAvailability ||= slot.available;
  }
  return [...days.values()];
}

/** Only the fulfilment types the market offers, delivery first. */
export function offeredTypes(options: Pick<FulfilmentOptions, 'delivery' | 'pickup'>): FulfilmentType[] {
  const types: FulfilmentType[] = [];
  if (options.delivery) {
    types.push('DELIVERY');
  }
  if (options.pickup) {
    types.push('PICKUP');
  }
  return types;
}
