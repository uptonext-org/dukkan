import type { MerchantOrderSettings, OrderSettings, PaymentMethod } from '../api/types';
import { hoursOverlap, type Interval } from './marketProfile';

/** The settings as the form edits them: numbers as typed text ('' = platform default). */
export interface SettingsForm {
  ordersEnabled: boolean;
  closed: boolean;
  closedFrom: string;
  closedUntil: string;
  deliveryCapacity: string;
  pickupCapacity: string;
  deliveryLeadMinutes: string;
  pickupLeadMinutes: string;
  daysAhead: string;
  ownDeliveryHours: boolean;
  /** Index 0 = Monday … 6 = Sunday. */
  deliveryHours: Interval[][];
  autoAccept: boolean;
  paymentMethods: PaymentMethod[];
  defaultMaxOrderQuantity: string;
}

/** Form field → i18n key of the problem. */
export type SettingsProblems = Partial<Record<keyof SettingsForm, string>>;

export const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'CARD'];

const NUMBER_RANGES = {
  deliveryCapacity: [1, 99],
  pickupCapacity: [1, 99],
  deliveryLeadMinutes: [0, 2880],
  pickupLeadMinutes: [0, 2880],
  daysAhead: [1, 14],
  defaultMaxOrderQuantity: [1, 99],
} as const satisfies Partial<Record<keyof SettingsForm, readonly [number, number]>>;

type NumberField = keyof typeof NUMBER_RANGES;

const NUMBER_FIELDS = Object.keys(NUMBER_RANGES) as NumberField[];

function text(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value);
}

export function fromSettings(settings: MerchantOrderSettings): SettingsForm {
  const deliveryHours: Interval[][] = [[], [], [], [], [], [], []];
  for (const h of settings.deliveryHours) {
    deliveryHours[h.dayOfWeek - 1]?.push({ opensAt: h.opensAt, closesAt: h.closesAt });
  }
  return {
    ordersEnabled: settings.ordersEnabled,
    closed: settings.closedFrom != null,
    closedFrom: settings.closedFrom ?? '',
    closedUntil: settings.closedUntil ?? '',
    deliveryCapacity: text(settings.deliveryCapacity),
    pickupCapacity: text(settings.pickupCapacity),
    deliveryLeadMinutes: text(settings.deliveryLeadMinutes),
    pickupLeadMinutes: text(settings.pickupLeadMinutes),
    daysAhead: text(settings.daysAhead),
    ownDeliveryHours: settings.deliveryHours.length > 0,
    deliveryHours,
    autoAccept: settings.autoAccept,
    paymentMethods: settings.paymentMethods,
    defaultMaxOrderQuantity: text(settings.defaultMaxOrderQuantity),
  };
}

/**
 * Checks the form like the server does and builds the request, or names every problem. The pause
 * is not part of the form (it is switched on and off at once); {@code pausedUntil} is passed on.
 */
export function toSettings(form: SettingsForm, pausedUntil: string | null): { settings: OrderSettings } | { problems: SettingsProblems } {
  const problems: SettingsProblems = {};
  const numbers: Partial<Record<NumberField, number | null>> = {};
  for (const field of NUMBER_FIELDS) {
    const raw = form[field].trim();
    if (raw === '') {
      numbers[field] = null;
      continue;
    }
    const [min, max] = NUMBER_RANGES[field];
    const value = Number(raw);
    if (!/^\d+$/.test(raw) || value < min || value > max) {
      problems[field] = 'settings.problem.range';
    } else {
      numbers[field] = value;
    }
  }
  if (form.closed) {
    if (form.closedFrom === '' || form.closedUntil === '') {
      problems.closedUntil = 'settings.problem.closedBoth';
    } else if (form.closedUntil < form.closedFrom) {
      problems.closedUntil = 'settings.problem.closedOrder';
    }
  }
  const deliveryHours = form.ownDeliveryHours ? form.deliveryHours : [[], [], [], [], [], [], []];
  if (form.ownDeliveryHours) {
    const intervals = deliveryHours.flat();
    if (intervals.length === 0) {
      problems.deliveryHours = 'settings.problem.noDeliveryHours';
    } else if (intervals.some((i) => i.opensAt === '' || i.closesAt === '')) {
      problems.deliveryHours = 'profile.problem.time';
    } else if (intervals.some((i) => i.opensAt === i.closesAt)) {
      problems.deliveryHours = 'profile.problem.sameTime';
    } else if (hoursOverlap(deliveryHours)) {
      problems.deliveryHours = 'profile.problem.overlap';
    }
  }
  if (form.paymentMethods.length === 0) {
    problems.paymentMethods = 'settings.problem.payment';
  }
  if (Object.keys(problems).length > 0) {
    return { problems };
  }
  return {
    settings: {
      ordersEnabled: form.ordersEnabled,
      pausedUntil,
      closedFrom: form.closed ? form.closedFrom : null,
      closedUntil: form.closed ? form.closedUntil : null,
      deliveryCapacity: numbers.deliveryCapacity ?? null,
      pickupCapacity: numbers.pickupCapacity ?? null,
      deliveryLeadMinutes: numbers.deliveryLeadMinutes ?? null,
      pickupLeadMinutes: numbers.pickupLeadMinutes ?? null,
      daysAhead: numbers.daysAhead ?? null,
      deliveryHours: deliveryHours.flatMap((day, index) => day.map((i) => ({ dayOfWeek: index + 1, opensAt: i.opensAt, closesAt: i.closesAt }))),
      autoAccept: form.autoAccept,
      paymentMethods: PAYMENT_METHODS.filter((m) => form.paymentMethods.includes(m)),
      defaultMaxOrderQuantity: numbers.defaultMaxOrderQuantity ?? null,
    },
  };
}

/** The stored settings with only the pause changed, for the immediate pause buttons. */
export function withPause(settings: MerchantOrderSettings, pausedUntil: string | null): OrderSettings {
  return {
    ordersEnabled: settings.ordersEnabled,
    pausedUntil,
    closedFrom: settings.closedFrom ?? null,
    closedUntil: settings.closedUntil ?? null,
    deliveryCapacity: settings.deliveryCapacity ?? null,
    pickupCapacity: settings.pickupCapacity ?? null,
    deliveryLeadMinutes: settings.deliveryLeadMinutes ?? null,
    pickupLeadMinutes: settings.pickupLeadMinutes ?? null,
    daysAhead: settings.daysAhead ?? null,
    deliveryHours: settings.deliveryHours,
    autoAccept: settings.autoAccept,
    paymentMethods: settings.paymentMethods,
    defaultMaxOrderQuantity: settings.defaultMaxOrderQuantity ?? null,
  };
}

/** Next local 06:00 after {@code now}: "no more orders today". */
export function endOfDay(now: Date): Date {
  const next = new Date(now);
  next.setHours(6, 0, 0, 0);
  if (next <= now) {
    next.setDate(next.getDate() + 1);
  }
  return next;
}
