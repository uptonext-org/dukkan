import type {
  CreateListingRequest,
  FulfilmentOptions,
  Listing,
  ListingDetails,
  ListingOverrides,
  LocalizedText,
  MarketProfileRequest,
  MarketTag,
  MerchantMarket,
  NewCatalogProduct,
  OpeningHours,
  PromotionRequest,
  ProductDraft,
  PromotionType,
  UpdateListingRequest,
} from '../../shared/api';
import { centsToInput, parseMoneyInput } from '../../shared/format';
import type { Language } from '../../shared/i18n';
import type { ValidationKey } from '../auth/validation';

export type Errors<K extends string> = Partial<Record<K, ValidationKey>>;

export const CONTENT_LANGUAGES: readonly Language[] = ['de', 'en', 'ar'];

const TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
const BARCODE = /^\d{8,14}$/;
// The server's seller-information rules (MarketReview): a market cannot be approved without them.
const POSTCODE = /(^|\D)\d{5}(\D|$)/;
const TWO_WORDS = /\S+\s+\S+/;

function localized(values: Record<Language, string>): LocalizedText {
  const result: LocalizedText = {};
  for (const lang of CONTENT_LANGUAGES) {
    const text = values[lang].trim();
    if (text !== '') {
      result[lang] = text;
    }
  }
  return result;
}

export function emptyLocalized(): Record<Language, string> {
  return { de: '', en: '', ar: '' };
}

/** Comma/semicolon/newline separated list → trimmed unique items. */
export function splitList(input: string): string[] {
  return [...new Set(input.split(/[,;\n،]/).map((s) => s.trim()).filter((s) => s !== ''))];
}

// ---------------------------------------------------------------- new product + listing

export interface NewProductValues {
  name: Record<Language, string>;
  description: Record<Language, string>;
  keywords: string;
  brand: string;
  unitSize: string;
  categoryId: string | null;
  cuisineCodes: string[];
  organic: boolean;
  /** Allergen codes (ALLERGEN_CODES). */
  allergens: string[];
  price: string;
  maxOrderQuantity: string;
  /** Uploaded product photo (URL), or null. */
  imageUrl: string | null;
  /** Net quantity for the comparison price; only set from a capture draft. */
  net: { quantity: number; unit: 'kg' | 'l' | 'piece' } | null;
  /** Only set from a capture draft (read from the photo). */
  barcode: string | null;
}

export type NewProductField = 'name' | 'description' | 'keywords' | 'brand' | 'unitSize' | 'categoryId' | 'price' | 'maxOrderQuantity' | 'barcode';

export function emptyNewProduct(): NewProductValues {
  return {
    name: emptyLocalized(),
    description: emptyLocalized(),
    keywords: '',
    brand: '',
    unitSize: '',
    categoryId: null,
    cuisineCodes: [],
    organic: false,
    allergens: [],
    price: '',
    maxOrderQuantity: '24',
    imageUrl: null,
    net: null,
    barcode: null,
  };
}

/**
 * Fills the form from a capture draft. What the draft knows replaces the field; what it does not
 * know keeps what the merchant already entered. Allergens are merged, never removed.
 */
export function fromDraft(draft: ProductDraft, current: NewProductValues): NewProductValues {
  const p = draft.product;
  const pick = (values: Record<Language, string>, from: Partial<Record<string, string>>) => {
    const next = { ...values };
    for (const lang of CONTENT_LANGUAGES) {
      const text = from[lang];
      if (text !== undefined && text.trim() !== '') {
        next[lang] = text;
      }
    }
    return next;
  };
  return {
    ...current,
    name: pick(current.name, p.name),
    description: pick(current.description, p.description),
    keywords: p.keywords.length > 0 ? p.keywords.join(', ') : current.keywords,
    brand: p.brand ?? current.brand,
    unitSize: p.unitSize ?? current.unitSize,
    categoryId: p.categoryId ?? current.categoryId,
    cuisineCodes: p.cuisineCodes.length > 0 ? p.cuisineCodes : current.cuisineCodes,
    organic: p.organic || current.organic,
    allergens: [...new Set([...current.allergens, ...p.allergens])],
    price: draft.price != null ? centsToInput(draft.price.amountCents) : current.price,
    imageUrl: p.imageUrl ?? current.imageUrl,
    net: p.netQuantity != null && p.netUnit != null ? { quantity: p.netQuantity, unit: p.netUnit } : current.net,
    barcode: p.barcode ?? current.barcode,
  };
}

/** Price input → error key, or undefined when it is a positive amount with at most 2 decimals. */
export function validatePrice(input: string): ValidationKey | undefined {
  if (input.trim() === '') {
    return 'required';
  }
  const cents = parseMoneyInput(input);
  return cents === null || cents <= 0 ? 'price' : undefined;
}

function validateMaxQuantity(input: string): ValidationKey | undefined {
  const value = Number(input);
  return Number.isInteger(value) && value >= 1 && value <= 99 ? undefined : 'maxQuantity';
}

/**
 * Create-product form rules (contract NewCatalogProduct + CreateListingRequest): a name in the
 * market's primary language, unit size 1–40, a category, ≤ 30 keywords of ≤ 60 chars, brand ≤ 80,
 * price > 0 with ≤ 2 decimals, max quantity 1–99.
 */
export function validateNewProduct(values: NewProductValues, primaryLocale: Language): Errors<NewProductField> {
  const errors: Errors<NewProductField> = {};
  if (values.name[primaryLocale].trim() === '') {
    errors.name = 'namePrimaryRequired';
  } else if (CONTENT_LANGUAGES.some((l) => values.name[l].trim().length > 200)) {
    errors.name = 'tooLong';
  }
  const unit = values.unitSize.trim();
  if (unit === '') {
    errors.unitSize = 'required';
  } else if (unit.length > 40) {
    errors.unitSize = 'tooLong';
  }
  if (values.categoryId === null) {
    errors.categoryId = 'categoryRequired';
  }
  if (CONTENT_LANGUAGES.some((l) => values.description[l].trim().length > 2000)) {
    errors.description = 'tooLong';
  }
  const keywords = splitList(values.keywords);
  if (keywords.length > 30 || keywords.some((k) => k.length > 60)) {
    errors.keywords = 'keywords';
  }
  if (values.brand.trim().length > 80) {
    errors.brand = 'tooLong';
  }
  const price = validatePrice(values.price);
  if (price !== undefined) {
    errors.price = price;
  }
  const max = validateMaxQuantity(values.maxOrderQuantity);
  if (max !== undefined) {
    errors.maxOrderQuantity = max;
  }
  if (values.barcode !== null && !BARCODE.test(values.barcode)) {
    errors.barcode = 'barcode';
  }
  return errors;
}

function toNewCatalogProduct(values: NewProductValues): NewCatalogProduct {
  const brand = values.brand.trim();
  const description = localized(values.description);
  return {
    name: localized(values.name),
    ...(Object.keys(description).length > 0 ? { description } : {}),
    keywords: splitList(values.keywords),
    brand: brand === '' ? null : brand,
    unitSize: values.unitSize.trim(),
    categoryId: values.categoryId ?? '',
    cuisineCodes: values.cuisineCodes,
    organic: values.organic,
    allergens: values.allergens,
    ...(values.imageUrl !== null ? { imageUrl: values.imageUrl } : {}),
    ...(values.net !== null ? { netQuantity: values.net.quantity, netUnit: values.net.unit } : {}),
    ...(values.barcode !== null ? { barcode: values.barcode } : {}),
  };
}

export function toNewProductListing(values: NewProductValues): CreateListingRequest {
  return {
    catalogProductId: null,
    newProduct: toNewCatalogProduct(values),
    price: { amountCents: parseMoneyInput(values.price) ?? 0, currency: 'EUR' },
    available: true,
    maxOrderQuantity: Number(values.maxOrderQuantity),
  };
}

// ---------------------------------------------------------------- edit a listing

function fromLocalized(text: LocalizedText | undefined): Record<Language, string> {
  return { de: text?.de ?? '', en: text?.en ?? '', ar: text?.ar ?? '' };
}

/** The edit form of the market's own product, filled with its current details. */
export function fromListingDetails({ listing, product }: ListingDetails): NewProductValues {
  return {
    name: fromLocalized(product.name),
    description: fromLocalized(product.description),
    keywords: (product.keywords ?? []).join(', '),
    brand: product.brand ?? '',
    unitSize: product.unitSize,
    categoryId: product.categoryId,
    cuisineCodes: product.cuisineCodes ?? [],
    organic: product.organic ?? false,
    allergens: product.allergens ?? [],
    price: centsToInput(listing.price.amountCents),
    maxOrderQuantity: String(listing.maxOrderQuantity),
    imageUrl: listing.imageUrl ?? null,
    net: product.netQuantity != null && product.netUnit != null ? { quantity: product.netQuantity, unit: product.netUnit } : null,
    barcode: product.barcode ?? null,
  };
}

/**
 * When merchants write only in the market's language, send only that language: the server keeps the
 * other languages (the merchant's own texts) and has outdated machine translations rewritten.
 */
export function onlyLanguage(values: NewProductValues, language: Language): NewProductValues {
  const one = (texts: Record<Language, string>) => ({ ...emptyLocalized(), [language]: texts[language] });
  return { ...values, name: one(values.name), description: one(values.description) };
}

/** Everything of the market's own product at once: details, photo, price and limit. */
export function toProductUpdate(values: NewProductValues): UpdateListingRequest {
  return {
    product: toNewCatalogProduct(values),
    imageUrl: values.imageUrl,
    price: { amountCents: parseMoneyInput(values.price) ?? 0, currency: 'EUR' },
    maxOrderQuantity: Number(values.maxOrderQuantity),
  };
}

/** The market's own details of a catalog product in the edit form; empty = the catalog value. */
export interface CatalogListingValues extends OwnDetails {
  price: string;
  maxOrderQuantity: string;
  imageUrl: string | null;
}

export function fromCatalogListing(listing: Listing): CatalogListingValues {
  return {
    name: listing.overrides?.name ?? '',
    description: listing.overrides?.description ?? '',
    unitSize: listing.overrides?.unitSize ?? '',
    brand: listing.overrides?.brand ?? '',
    price: centsToInput(listing.price.amountCents),
    maxOrderQuantity: String(listing.maxOrderQuantity),
    imageUrl: listing.imageUrl ?? null,
  };
}

export type CatalogListingField = 'name' | 'description' | 'unitSize' | 'brand' | 'price' | 'maxOrderQuantity';

export function validateCatalogListing(values: CatalogListingValues): Errors<CatalogListingField> {
  const errors: Errors<CatalogListingField> = {};
  const limits: [CatalogListingField & keyof OwnDetails, number][] = [['name', 200], ['description', 2000], ['unitSize', 40], ['brand', 80]];
  for (const [field, max] of limits) {
    if (values[field].trim().length > max) {
      errors[field] = 'tooLong';
    }
  }
  const price = validatePrice(values.price);
  if (price !== undefined) {
    errors.price = price;
  }
  const max = validateMaxQuantity(values.maxOrderQuantity);
  if (max !== undefined) {
    errors.maxOrderQuantity = max;
  }
  return errors;
}

/** Own details (all empty = the catalog values again), photo, price and limit of a catalog listing. */
export function toCatalogListingUpdate(values: CatalogListingValues): UpdateListingRequest {
  const own = (text: string) => (text.trim() === '' ? null : text.trim());
  return {
    overrides: { name: own(values.name), description: own(values.description), unitSize: own(values.unitSize), brand: own(values.brand) },
    imageUrl: values.imageUrl,
    price: { amountCents: parseMoneyInput(values.price) ?? 0, currency: 'EUR' },
    maxOrderQuantity: Number(values.maxOrderQuantity),
  };
}

/** What the merchant changed of a catalog product; fields equal to the catalog value are left out. */
export interface OwnDetails {
  name: string;
  description: string;
  unitSize: string;
  brand: string;
}

export function toOverrides(own: OwnDetails, catalog: { name: string; unitSize: string; brand?: string | null }): ListingOverrides | null {
  const changed = (value: string, original: string | null | undefined) => {
    const trimmed = value.trim();
    return trimmed === '' || trimmed === (original ?? '').trim() ? null : trimmed;
  };
  const overrides: ListingOverrides = {
    name: changed(own.name, catalog.name),
    description: changed(own.description, ''),
    unitSize: changed(own.unitSize, catalog.unitSize),
    brand: changed(own.brand, catalog.brand),
  };
  return Object.values(overrides).every((v) => v === null) ? null : overrides;
}

export function toCatalogListing(catalogProductId: string, price: string, overrides: ListingOverrides | null = null): CreateListingRequest {
  return {
    catalogProductId,
    newProduct: null,
    price: { amountCents: parseMoneyInput(price) ?? 0, currency: 'EUR' },
    available: true,
    maxOrderQuantity: 24,
    ...(overrides !== null ? { overrides } : {}),
  };
}

// ---------------------------------------------------------------- market profile

export interface TimeRange {
  opensAt: string;
  /** Earlier than (or equal to midnight after) `opensAt` means the next day. */
  closesAt: string;
}

export interface DayHours {
  open: boolean;
  /** At least one; several for e.g. a lunch break. */
  intervals: TimeRange[];
}

export const MAX_INTERVALS_PER_DAY = 3;

const minutes = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));

/** True when the range ends on the next day (`18:00`–`02:00`, `20:00`–`00:00`). */
export function endsNextDay(range: TimeRange): boolean {
  return TIME.test(range.opensAt) && TIME.test(range.closesAt) && minutes(range.closesAt) < minutes(range.opensAt);
}

/** Same rule as the server: no two intervals share a minute of the week (also across midnight and Sunday → Monday). */
export function hoursOverlap(hours: DayHours[]): boolean {
  const week = 7 * 24 * 60;
  const spans = hours
    .flatMap((day, index) =>
      day.open
        ? day.intervals.map((r) => {
            const start = index * 24 * 60 + minutes(r.opensAt);
            const length = (minutes(r.closesAt) - minutes(r.opensAt) + 24 * 60) % (24 * 60);
            return { start, end: start + length };
          })
        : [],
    )
    .sort((a, b) => a.start - b.start);
  return spans.some((span, i) => {
    if (spans.length < 2) {
      return false;
    }
    const next = spans[(i + 1) % spans.length];
    return next !== undefined && span.end > next.start + (i + 1 === spans.length ? week : 0);
  });
}

export interface MarketProfileValues {
  name: string;
  typeCode: string | null;
  cuisineCodes: string[];
  description: Record<Language, string>;
  /** Descriptions in languages the app does not edit; sent back unchanged so they are not lost. */
  otherDescriptions?: LocalizedText;
  primaryLocale: Language;
  addressLine: string;
  location: { lat: number; lng: number } | null;
  phone: string;
  logoUrl: string;
  coverImageUrl: string;
  tags: MarketTag[];
  delivery: boolean;
  pickup: boolean;
  deliveryRadiusKm: string;
  deliveryFee: string;
  freeDeliveryFrom: string;
  minimumOrderValue: string;
  /** Index 0 = Monday … 6 = Sunday. One interval per day in this editor. */
  hours: DayHours[];
  legalName: string;
  registerInfo: string;
}

export type MarketProfileField =
  | 'name'
  | 'typeCode'
  | 'cuisineCodes'
  | 'addressLine'
  | 'phone'
  | 'logoUrl'
  | 'coverImageUrl'
  | 'fulfilment'
  | 'deliveryRadiusKm'
  | 'deliveryFee'
  | 'freeDeliveryFrom'
  | 'minimumOrderValue'
  | 'hours'
  | 'legalName'
  | 'registerInfo';

export function emptyMarketProfile(primaryLocale: Language): MarketProfileValues {
  return {
    name: '',
    typeCode: null,
    cuisineCodes: [],
    description: emptyLocalized(),
    primaryLocale,
    addressLine: '',
    location: null,
    phone: '',
    logoUrl: '',
    coverImageUrl: '',
    tags: [],
    delivery: true,
    pickup: true,
    deliveryRadiusKm: '5',
    deliveryFee: '2,99',
    freeDeliveryFrom: '',
    minimumOrderValue: '15',
    hours: Array.from({ length: 7 }, (_, i) => ({ open: i < 6, intervals: [{ opensAt: '08:00', closesAt: '20:00' }] })),
    legalName: '',
    registerInfo: '',
  };
}

const URL_PATTERN = /^https?:\/\/\S+$/i;

function optionalMoney(input: string): ValidationKey | undefined {
  if (input.trim() === '') {
    return undefined;
  }
  const cents = parseMoneyInput(input);
  return cents === null ? 'price' : undefined;
}

export function validateMarketProfile(values: MarketProfileValues): Errors<MarketProfileField> {
  const errors: Errors<MarketProfileField> = {};
  const name = values.name.trim();
  if (name.length < 2) {
    errors.name = name === '' ? 'required' : 'tooShort';
  } else if (name.length > 80) {
    errors.name = 'tooLong';
  }
  if (values.typeCode === null) {
    errors.typeCode = 'required';
  }
  if (values.cuisineCodes.length > 5) {
    errors.cuisineCodes = 'maxCuisines';
  }
  if (values.location === null || values.addressLine.trim().length < 5) {
    errors.addressLine = 'addressFromSearch';
  } else if (!POSTCODE.test(values.addressLine)) {
    errors.addressLine = 'addressPostcode';
  }
  const phone = values.phone.trim();
  if (phone === '') {
    errors.phone = 'required';
  } else if (phone.length > 30) {
    errors.phone = 'tooLong';
  } else if (phone.replace(/\D/g, '').length < 6) {
    errors.phone = 'phoneDigits';
  }
  for (const field of ['logoUrl', 'coverImageUrl'] as const) {
    const url = values[field].trim();
    if (url !== '' && !URL_PATTERN.test(url)) {
      errors[field] = 'url';
    }
  }
  if (!values.delivery && !values.pickup) {
    errors.fulfilment = 'fulfilmentRequired';
  }
  if (values.delivery) {
    const radius = Number(values.deliveryRadiusKm.replace(',', '.'));
    if (!Number.isFinite(radius) || radius < 0.5 || radius > 50) {
      errors.deliveryRadiusKm = 'radius';
    }
    const fee = optionalMoney(values.deliveryFee);
    if (fee !== undefined) {
      errors.deliveryFee = fee;
    }
    const free = optionalMoney(values.freeDeliveryFrom);
    if (free !== undefined) {
      errors.freeDeliveryFrom = free;
    }
  }
  if (values.minimumOrderValue.trim() === '' || parseMoneyInput(values.minimumOrderValue) === null) {
    errors.minimumOrderValue = 'price';
  }
  const openDays = values.hours.filter((d) => d.open);
  if (openDays.length === 0) {
    errors.hours = 'hoursRequired';
  } else if (openDays.some((d) => d.intervals.length === 0 || d.intervals.some((r) => !TIME.test(r.opensAt) || !TIME.test(r.closesAt) || r.opensAt === r.closesAt))) {
    errors.hours = 'hours';
  } else if (hoursOverlap(values.hours)) {
    errors.hours = 'hoursOverlap';
  }
  const legal = values.legalName.trim();
  if (legal === '') {
    errors.legalName = 'required';
  } else if (legal.length > 120) {
    errors.legalName = 'tooLong';
  } else if (!TWO_WORDS.test(legal)) {
    errors.legalName = 'legalNameWords';
  }
  if (values.registerInfo.trim().length > 120) {
    errors.registerInfo = 'tooLong';
  }
  return errors;
}

const money = (input: string) => ({ amountCents: parseMoneyInput(input) ?? 0, currency: 'EUR' });
const optional = (input: string) => (input.trim() === '' ? null : input.trim());

export function toMarketProfileRequest(values: MarketProfileValues): MarketProfileRequest {
  const fulfilment: FulfilmentOptions = {
    delivery: values.delivery,
    pickup: values.pickup,
    deliveryRadiusKm: values.delivery ? Number(values.deliveryRadiusKm.replace(',', '.')) : null,
    deliveryFee: values.delivery && values.deliveryFee.trim() !== '' ? money(values.deliveryFee) : null,
    freeDeliveryFrom: values.delivery && values.freeDeliveryFrom.trim() !== '' ? money(values.freeDeliveryFrom) : null,
    minimumOrderValue: money(values.minimumOrderValue),
  };
  const openingHours: OpeningHours[] = values.hours.flatMap((day, index) =>
    day.open ? day.intervals.map((r) => ({ dayOfWeek: index + 1, opensAt: r.opensAt, closesAt: r.closesAt })) : [],
  );
  const description = { ...values.otherDescriptions, ...localized(values.description) };
  return {
    name: values.name.trim(),
    typeCode: values.typeCode ?? '',
    cuisineCodes: values.cuisineCodes,
    ...(Object.keys(description).length > 0 ? { description } : {}),
    primaryLocale: values.primaryLocale,
    addressLine: values.addressLine.trim(),
    location: values.location ?? { lat: 0, lng: 0 },
    phone: optional(values.phone),
    logoUrl: optional(values.logoUrl),
    coverImageUrl: optional(values.coverImageUrl),
    tags: values.tags,
    fulfilment,
    openingHours,
    legalName: values.legalName.trim(),
    registerInfo: optional(values.registerInfo),
  };
}

/**
 * Prefill from an existing market. All languages come from `descriptions`; saving replaces every
 * language, so an empty field here would delete that translation. Older servers only send the
 * requested language in `description`.
 */
export function fromMerchantMarket(market: MerchantMarket, language: Language): MarketProfileValues {
  const base = emptyMarketProfile(CONTENT_LANGUAGES.includes(market.primaryLocale as Language) ? (market.primaryLocale as Language) : 'de');
  const hours = base.hours.map((_, index): DayHours => {
    const intervals = market.openingHours
      .filter((h) => h.dayOfWeek === index + 1)
      .sort((a, b) => a.opensAt.localeCompare(b.opensAt))
      .map((h) => ({ opensAt: h.opensAt, closesAt: h.closesAt }));
    return intervals.length > 0 ? { open: true, intervals } : { open: false, intervals: [{ opensAt: '08:00', closesAt: '20:00' }] };
  });
  const f = market.fulfilment;
  return {
    ...base,
    name: market.name,
    typeCode: market.type.code,
    cuisineCodes: market.cuisines.map((c) => c.code),
    description:
      market.descriptions !== undefined
        ? { ...base.description, ...Object.fromEntries(CONTENT_LANGUAGES.map((l) => [l, market.descriptions?.[l] ?? ''])) }
        : { ...base.description, [language]: market.description ?? '' },
    otherDescriptions: Object.fromEntries(Object.entries(market.descriptions ?? {}).filter(([code]) => !CONTENT_LANGUAGES.includes(code as Language))),
    addressLine: market.addressLine,
    location: market.location,
    phone: market.phone ?? '',
    logoUrl: market.logoUrl ?? '',
    coverImageUrl: market.coverImageUrl ?? '',
    tags: market.tags,
    delivery: f.delivery,
    pickup: f.pickup,
    deliveryRadiusKm: f.deliveryRadiusKm != null ? String(f.deliveryRadiusKm).replace('.', ',') : '',
    deliveryFee: f.deliveryFee != null ? centsToInput(f.deliveryFee.amountCents) : '',
    freeDeliveryFrom: f.freeDeliveryFrom != null ? centsToInput(f.freeDeliveryFrom.amountCents) : '',
    minimumOrderValue: centsToInput(f.minimumOrderValue.amountCents),
    hours,
    legalName: market.imprint.legalName,
    registerInfo: market.imprint.registerInfo ?? '',
  };
}

// ---------------------------------------------------------------- promotions

/** One quantity price of a multi-buy: `quantity` units together for `price`. */
export interface TierValues {
  quantity: string;
  price: string;
}

export interface PromotionValues {
  title: Record<Language, string>;
  type: PromotionType;
  percentOff: string;
  /** MULTI_BUY: e.g. 3 for 5 € and 7 for 10 €. */
  tiers: TierValues[];
  /** FIXED_PRICE: offer price per selected product id. */
  offers: Record<string, string>;
  /** 0 = starts now, else at midnight that many days from today. */
  startInDays: number;
  days: number;
  /** ISO weekdays (1 = Monday); all seven = every day. */
  weekdays: number[];
  allDay: boolean;
  /** "HH:mm", Berlin time, used when not all day. */
  dailyFrom: string;
  dailyUntil: string;
  productIds: string[];
}

export type PromotionField = 'title' | 'percentOff' | 'tiers' | 'offers' | 'days' | 'weekdays' | 'daily' | 'productIds';

export const ALL_WEEKDAYS = [1, 2, 3, 4, 5, 6, 7];
const MAX_TIERS = 5;

export function emptyPromotion(): PromotionValues {
  return {
    title: emptyLocalized(),
    type: 'PERCENT_OFF',
    percentOff: '20',
    tiers: [{ quantity: '3', price: '' }],
    offers: {},
    startInDays: 0,
    days: 7,
    weekdays: ALL_WEEKDAYS,
    allDay: true,
    dailyFrom: '08:00',
    dailyUntil: '12:00',
    productIds: [],
  };
}

/** Start of the promotion: now, or midnight `startInDays` days from today (device time). */
export function promotionStart(values: Pick<PromotionValues, 'startInDays'>, now: Date = new Date()): Date {
  if (values.startInDays <= 0) {
    return now;
  }
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() + values.startInDays);
  return start;
}

/** End of the promotion: `days` after its start. */
export function promotionEnd(values: Pick<PromotionValues, 'days' | 'startInDays'>, now: Date = new Date()): Date {
  return new Date(promotionStart(values, now).getTime() + values.days * 24 * 60 * 60 * 1000);
}

/**
 * Checks what the server checks too. `regularPrices` (cents per product id) lets an offer price be
 * checked against the product's price before sending.
 */
export function validatePromotion(
  values: PromotionValues,
  primaryLocale: Language,
  now: Date = new Date(),
  regularPrices: Record<string, number> = {},
): Errors<PromotionField> {
  const errors: Errors<PromotionField> = {};
  // Contract: validUntil after validFrom and in the future (server field code `Future`).
  if (!Number.isFinite(values.days) || values.days <= 0 || promotionEnd(values, now).getTime() <= now.getTime()) {
    errors.days = 'endDateFuture';
  }
  if (values.title[primaryLocale].trim() === '') {
    errors.title = 'namePrimaryRequired';
  }
  if (values.type === 'PERCENT_OFF') {
    const pct = Number(values.percentOff);
    if (!Number.isInteger(pct) || pct < 1 || pct > 90) {
      errors.percentOff = 'percent';
    }
  } else if (values.type === 'MULTI_BUY') {
    const quantities = values.tiers.map((tier) => Number(tier.quantity));
    const invalid =
      values.tiers.length === 0 ||
      values.tiers.length > MAX_TIERS ||
      quantities.some((q) => !Number.isInteger(q) || q < 2 || q > 99) ||
      new Set(quantities).size !== quantities.length ||
      values.tiers.some((tier) => validatePrice(tier.price) !== undefined);
    if (invalid) {
      errors.tiers = 'tiers';
    }
  } else {
    const wrong = values.productIds.some((id) => {
      const offer = parseMoneyInput(values.offers[id] ?? '');
      const regular = regularPrices[id];
      return offer === null || validatePrice(values.offers[id] ?? '') !== undefined || (regular !== undefined && offer >= regular);
    });
    if (wrong) {
      errors.offers = 'offerBelowPrice';
    }
  }
  if (values.weekdays.length === 0) {
    errors.weekdays = 'weekdaysRequired';
  }
  if (!values.allDay && !(TIME.test(values.dailyFrom) && TIME.test(values.dailyUntil) && values.dailyFrom < values.dailyUntil)) {
    errors.daily = 'dailyWindow';
  }
  if (values.productIds.length === 0) {
    errors.productIds = 'productsRequired';
  } else if (values.productIds.length > 200) {
    errors.productIds = 'tooLong';
  }
  return errors;
}

export function toPromotionRequest(values: PromotionValues, now: Date = new Date()): PromotionRequest {
  return {
    title: localized(values.title),
    type: values.type,
    percentOff: values.type === 'PERCENT_OFF' ? Number(values.percentOff) : null,
    tiers: values.type === 'MULTI_BUY' ? values.tiers.map((tier) => ({ quantity: Number(tier.quantity), price: money(tier.price) })) : null,
    offers: values.type === 'FIXED_PRICE' ? values.productIds.map((id) => ({ productId: id, offerPrice: money(values.offers[id] ?? '') })) : null,
    validFrom: promotionStart(values, now).toISOString(),
    validUntil: promotionEnd(values, now).toISOString(),
    weekdays: values.weekdays.length === 7 ? null : [...values.weekdays].sort((a, b) => a - b),
    dailyFrom: values.allDay ? null : values.dailyFrom,
    dailyUntil: values.allDay ? null : values.dailyUntil,
    productIds: values.productIds,
  };
}
