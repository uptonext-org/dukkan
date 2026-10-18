import type { DeliverySlot } from '../../shared/api';
import { dayKey, formatDayLabel } from '../../shared/format/dates';

export interface SlotDay {
  key: string;
  title: string;
  data: DeliverySlot[];
  hasAvailability: boolean;
}

/** Groups slots by calendar day in Europe/Berlin, preserving the server's start-time order. */
export function groupSlotsByDay(slots: readonly DeliverySlot[]): SlotDay[] {
  const days = new Map<string, SlotDay>();
  const sorted = [...slots].sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt));
  for (const slot of sorted) {
    const key = dayKey(slot.startsAt);
    let day = days.get(key);
    if (!day) {
      day = { key, title: formatDayLabel(slot.startsAt), data: [], hasAvailability: false };
      days.set(key, day);
    }
    day.data.push(slot);
    day.hasAvailability ||= slot.available;
  }
  return [...days.values()];
}
