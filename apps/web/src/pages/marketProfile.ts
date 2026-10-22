import type { GeoPoint, MarketProfileRequest, MarketTag, MerchantMarket, Money } from '../api/types';
import { centsToInput } from '../ui/format';

export const MARKET_TAGS: MarketTag[] = ['ORGANIC', 'REGIONAL', 'HALAL', 'VEGAN_FRIENDLY'];
export const MAX_CUISINES = 5;
export const MAX_INTERVALS_PER_DAY = 3;

export interface Interval {
  opensAt: string;
  closesAt: string;
}

/** The profile as the form edits it: amounts as typed text, opening hours grouped by weekday. */
export interface ProfileForm {
  name: string;
  typeCode: string;
  cuisineCodes: string[];
  descriptions: Record<string, string>;
  primaryLocale: string;
  addressLine: string;
  location: GeoPoint | null;
  phone: string;
  logoUrl: string | null;
  coverImageUrl: string | null;
  tags: MarketTag[];
  delivery: boolean;
  pickup: boolean;
  radiusKm: string;
  fee: string;
  freeFrom: string;
  minimum: string;
  /** Index 0 = Monday … 6 = Sunday. */
  hours: Interval[][];
  legalName: string;
  registerInfo: string;
}

/** Form field → i18n key of the problem. */
export type ProfileProblems = Partial<Record<keyof ProfileForm, string>>;

export function fromMarket(market: MerchantMarket, language: string): ProfileForm {
  const f = market.fulfilment;
  const money = (m: Money | null | undefined) => (m == null ? '' : centsToInput(m.amountCents, language));
  const hours: Interval[][] = [[], [], [], [], [], [], []];
  for (const h of market.openingHours ?? []) {
    hours[h.dayOfWeek - 1]?.push({ opensAt: h.opensAt, closesAt: h.closesAt });
  }
  // Older servers send only the requested language; it is better than an empty form.
  const descriptions = market.descriptions ?? (market.description != null ? { [language]: market.description } : {});
  return {
    name: market.name,
    typeCode: market.type.code,
    cuisineCodes: (market.cuisines ?? []).map((c) => c.code),
    descriptions: { ...descriptions },
    primaryLocale: market.primaryLocale,
    addressLine: market.addressLine,
    location: market.location ?? null,
    phone: market.phone ?? '',
    logoUrl: market.logoUrl ?? null,
    coverImageUrl: market.coverImageUrl ?? null,
    tags: [...(market.tags ?? [])],
    delivery: f?.delivery ?? false,
    pickup: f?.pickup ?? true,
    radiusKm: f?.deliveryRadiusKm == null ? '' : String(f.deliveryRadiusKm).replace('.', language === 'en' ? '.' : ','),
    fee: money(f?.deliveryFee),
    freeFrom: money(f?.freeDeliveryFrom),
    minimum: money(f?.minimumOrderValue) || '0',
    hours,
    legalName: market.imprint.legalName,
    registerInfo: market.imprint.registerInfo ?? '',
  };
}

/** `0`, `2,50`, `2.5 €` → cents (0 allowed); null when it is not an amount. */
export function parseAmount(text: string): number | null {
  const plain = text.replace(/€|\s/g, '');
  if (!/^\d{1,5}([.,]\d{1,2})?$/.test(plain)) {
    return null;
  }
  return Math.round(Number(plain.replace(',', '.')) * 100);
}

function parseKm(text: string): number | null {
  // Any number of decimals, like the server and the app (a radius saved there must stay savable).
  const plain = text.trim().replace(',', '.');
  if (!/^\d{1,2}(\.\d+)?$/.test(plain)) {
    return null;
  }
  const km = Number(plain);
  return km >= 0.5 && km <= 50 ? km : null;
}

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

function minutes(time: string): number {
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

/**
 * Whether two intervals of the week overlap. Intervals are minutes from Monday 00:00; one that
 * closes before it opens runs into the next day, and Sunday night runs into Monday.
 */
export function hoursOverlap(hours: Interval[][]): boolean {
  const week = 7 * 24 * 60;
  const spans: [number, number][] = [];
  hours.forEach((day, index) => {
    for (const { opensAt, closesAt } of day) {
      const start = index * 24 * 60 + minutes(opensAt);
      let end = index * 24 * 60 + minutes(closesAt);
      if (end <= start) {
        end += 24 * 60;
      }
      spans.push([start, end]);
      if (end > week) {
        spans.push([start - week, end - week]);
      }
    }
  });
  return spans.some(([s1, e1], i) => spans.some(([s2, e2], j) => i < j && s1 < e2 && s2 < e1));
}

/** Checks the form like the server does and builds the request, or names every problem. */
export function toRequest(form: ProfileForm): { request: MarketProfileRequest } | { problems: ProfileProblems } {
  const problems: ProfileProblems = {};
  const name = form.name.trim();
  if (name.length < 2 || name.length > 80) {
    problems.name = 'profile.problem.name';
  }
  if (form.typeCode === '') {
    problems.typeCode = 'profile.problem.type';
  }
  if (form.cuisineCodes.length > MAX_CUISINES) {
    problems.cuisineCodes = 'profile.problem.cuisines';
  }
  const addressLine = form.addressLine.trim();
  if (addressLine.length < 5 || addressLine.length > 200 || form.location === null) {
    problems.addressLine = 'profile.problem.address';
  }
  const phone = form.phone.trim();
  // Only the length, as on the server: stored numbers like "+49.40.123" or "040 123 (Laden)" stay valid.
  if (phone.length > 30) {
    problems.phone = 'profile.problem.phone';
  }
  if (!form.delivery && !form.pickup) {
    problems.delivery = 'profile.problem.fulfilment';
  }
  const radius = parseKm(form.radiusKm);
  const fee = parseAmount(form.fee);
  const freeFrom = form.freeFrom.trim() === '' ? null : parseAmount(form.freeFrom);
  if (form.delivery && radius === null) {
    problems.radiusKm = 'profile.problem.radius';
  }
  if (form.delivery && fee === null) {
    problems.fee = 'profile.problem.amount';
  }
  if (form.delivery && form.freeFrom.trim() !== '' && freeFrom === null) {
    problems.freeFrom = 'profile.problem.amount';
  }
  const minimum = parseAmount(form.minimum);
  if (minimum === null) {
    problems.minimum = 'profile.problem.amount';
  }
  const all = form.hours.flat();
  if (all.length === 0) {
    problems.hours = 'profile.problem.hoursRequired';
  } else if (all.some((h) => !TIME.test(h.opensAt) || !TIME.test(h.closesAt))) {
    problems.hours = 'profile.problem.time';
  } else if (all.some((h) => h.opensAt === h.closesAt)) {
    problems.hours = 'profile.problem.sameTime';
  } else if (form.hours.some((day) => day.length > MAX_INTERVALS_PER_DAY) || hoursOverlap(form.hours)) {
    problems.hours = 'profile.problem.overlap';
  }
  const legalName = form.legalName.trim();
  if (legalName.length < 2 || legalName.length > 120) {
    problems.legalName = 'profile.problem.legalName';
  }
  if (form.registerInfo.trim().length > 120) {
    problems.registerInfo = 'profile.problem.tooLong';
  }
  const description = Object.fromEntries(
    Object.entries(form.descriptions)
      .map(([code, text]): [string, string] => [code, text.trim()])
      .filter(([, text]) => text !== ''),
  );
  if (Object.values(description).some((text) => text.length > 2000)) {
    problems.descriptions = 'profile.problem.tooLong';
  }
  if (Object.keys(problems).length > 0 || form.location === null) {
    return { problems };
  }
  const eur = (cents: number): Money => ({ amountCents: cents, currency: 'EUR' });
  return {
    request: {
      name,
      typeCode: form.typeCode,
      cuisineCodes: form.cuisineCodes,
      ...(Object.keys(description).length > 0 ? { description } : {}),
      primaryLocale: form.primaryLocale,
      addressLine,
      location: form.location,
      phone: phone === '' ? null : phone,
      logoUrl: form.logoUrl,
      coverImageUrl: form.coverImageUrl,
      tags: form.tags,
      fulfilment: {
        delivery: form.delivery,
        pickup: form.pickup,
        deliveryRadiusKm: form.delivery ? radius : null,
        deliveryFee: form.delivery && fee !== null ? eur(fee) : null,
        freeDeliveryFrom: form.delivery && freeFrom !== null ? eur(freeFrom) : null,
        minimumOrderValue: eur(minimum ?? 0),
      },
      openingHours: form.hours.flatMap((day, index) => day.map((h) => ({ dayOfWeek: index + 1, opensAt: h.opensAt, closesAt: h.closesAt }))),
      legalName,
      registerInfo: form.registerInfo.trim() === '' ? null : form.registerInfo.trim(),
    },
  };
}

/** Server field errors (`openingHours[2]`, `fulfilment.deliveryFee`, …) → the form field they belong to. */
export function formField(serverField: string): keyof ProfileForm | null {
  const root = serverField.split(/[.[]/)[0] ?? '';
  const nested = serverField.split('.')[1]?.split('[')[0];
  switch (root) {
    case 'openingHours':
      return 'hours';
    case 'description':
      return 'descriptions';
    case 'location':
      return 'addressLine';
    case 'fulfilment':
      return nested === 'deliveryRadiusKm' ? 'radiusKm' : nested === 'deliveryFee' ? 'fee' : nested === 'freeDeliveryFrom' ? 'freeFrom' : nested === 'minimumOrderValue' ? 'minimum' : 'delivery';
    case 'name':
    case 'typeCode':
    case 'cuisineCodes':
    case 'primaryLocale':
    case 'addressLine':
    case 'phone':
    case 'logoUrl':
    case 'coverImageUrl':
    case 'tags':
    case 'legalName':
    case 'registerInfo':
      return root;
    default:
      return null;
  }
}
