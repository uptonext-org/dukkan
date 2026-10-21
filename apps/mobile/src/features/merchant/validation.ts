import type {
  CreateListingRequest,
  FulfilmentOptions,
  LocalizedText,
  MarketProfileRequest,
  MarketTag,
  MerchantMarket,
  OpeningHours,
  PromotionRequest,
  PromotionType,
} from '../../shared/api';
import { centsToInput, parseMoneyInput } from '../../shared/format';
import type { Language } from '../../shared/i18n';
import type { ValidationKey } from '../auth/validation';

export type Errors<K extends string> = Partial<Record<K, ValidationKey>>;

export const CONTENT_LANGUAGES: readonly Language[] = ['de', 'en', 'ar'];

const TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;

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
  keywords: string;
  brand: string;
  unitSize: string;
  categoryId: string | null;
  cuisineCodes: string[];
  organic: boolean;
  allergens: string;
  price: string;
  maxOrderQuantity: string;
  /** Uploaded product photo (URL), or null. */
  imageUrl: string | null;
}

export type NewProductField = 'name' | 'keywords' | 'brand' | 'unitSize' | 'categoryId' | 'price' | 'maxOrderQuantity';

export function emptyNewProduct(): NewProductValues {
  return {
    name: emptyLocalized(),
    keywords: '',
    brand: '',
    unitSize: '',
    categoryId: null,
    cuisineCodes: [],
    organic: false,
    allergens: '',
    price: '',
    maxOrderQuantity: '24',
    imageUrl: null,
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
  return errors;
}

export function toNewProductListing(values: NewProductValues): CreateListingRequest {
  const brand = values.brand.trim();
  const allergens = splitList(values.allergens);
  return {
    catalogProductId: null,
    newProduct: {
      name: localized(values.name),
      keywords: splitList(values.keywords),
      brand: brand === '' ? null : brand,
      unitSize: values.unitSize.trim(),
      categoryId: values.categoryId ?? '',
      cuisineCodes: values.cuisineCodes,
      organic: values.organic,
      allergens,
      ...(values.imageUrl !== null ? { imageUrl: values.imageUrl } : {}),
    },
    price: { amountCents: parseMoneyInput(values.price) ?? 0, currency: 'EUR' },
    available: true,
    maxOrderQuantity: Number(values.maxOrderQuantity),
  };
}

export function toCatalogListing(catalogProductId: string, price: string): CreateListingRequest {
  return {
    catalogProductId,
    newProduct: null,
    price: { amountCents: parseMoneyInput(price) ?? 0, currency: 'EUR' },
    available: true,
    maxOrderQuantity: 24,
  };
}

// ---------------------------------------------------------------- market profile

export interface DayHours {
  open: boolean;
  opensAt: string;
  closesAt: string;
}

export interface MarketProfileValues {
  name: string;
  typeCode: string | null;
  cuisineCodes: string[];
  description: Record<Language, string>;
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
    hours: Array.from({ length: 7 }, (_, i) => ({ open: i < 6, opensAt: '08:00', closesAt: '20:00' })),
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
  }
  if (values.phone.trim().length > 30) {
    errors.phone = 'tooLong';
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
  } else if (openDays.some((d) => !TIME.test(d.opensAt) || !TIME.test(d.closesAt) || d.opensAt >= d.closesAt)) {
    errors.hours = 'hours';
  }
  const legal = values.legalName.trim();
  if (legal.length < 2) {
    errors.legalName = legal === '' ? 'required' : 'tooShort';
  } else if (legal.length > 120) {
    errors.legalName = 'tooLong';
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
    day.open ? [{ dayOfWeek: index + 1, opensAt: day.opensAt, closesAt: day.closesAt }] : [],
  );
  const description = localized(values.description);
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
 * Prefill from an existing market. The API returns the description only in the requested
 * language, so it lands in that language's field; other languages start empty (see README).
 */
export function fromMerchantMarket(market: MerchantMarket, language: Language): MarketProfileValues {
  const base = emptyMarketProfile(CONTENT_LANGUAGES.includes(market.primaryLocale as Language) ? (market.primaryLocale as Language) : 'de');
  const hours = base.hours.map((_, index) => {
    const interval = market.openingHours.find((h) => h.dayOfWeek === index + 1);
    return interval ? { open: true, opensAt: interval.opensAt, closesAt: interval.closesAt } : { open: false, opensAt: '08:00', closesAt: '20:00' };
  });
  const f = market.fulfilment;
  return {
    ...base,
    name: market.name,
    typeCode: market.type.code,
    cuisineCodes: market.cuisines.map((c) => c.code),
    description: { ...base.description, [language]: market.description ?? '' },
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

export interface PromotionValues {
  title: Record<Language, string>;
  type: PromotionType;
  percentOff: string;
  bundleQuantity: string;
  bundlePrice: string;
  days: number;
  productIds: string[];
}

export type PromotionField = 'title' | 'percentOff' | 'bundleQuantity' | 'bundlePrice' | 'days' | 'productIds';

export function emptyPromotion(): PromotionValues {
  return { title: emptyLocalized(), type: 'PERCENT_OFF', percentOff: '20', bundleQuantity: '3', bundlePrice: '', days: 7, productIds: [] };
}

/** End of the promotion: `days` after `now` (validFrom). */
export function promotionEnd(values: Pick<PromotionValues, 'days'>, now: Date = new Date()): Date {
  return new Date(now.getTime() + values.days * 24 * 60 * 60 * 1000);
}

export function validatePromotion(values: PromotionValues, primaryLocale: Language, now: Date = new Date()): Errors<PromotionField> {
  const errors: Errors<PromotionField> = {};
  // Contract: validUntil after validFrom and in the future (server field code `Future`).
  if (!Number.isFinite(values.days) || promotionEnd(values, now).getTime() <= now.getTime()) {
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
  } else {
    const qty = Number(values.bundleQuantity);
    if (!Number.isInteger(qty) || qty < 2 || qty > 20) {
      errors.bundleQuantity = 'bundleQuantity';
    }
    const price = validatePrice(values.bundlePrice);
    if (price !== undefined) {
      errors.bundlePrice = price;
    }
  }
  if (values.productIds.length === 0) {
    errors.productIds = 'productsRequired';
  } else if (values.productIds.length > 200) {
    errors.productIds = 'tooLong';
  }
  return errors;
}

export function toPromotionRequest(values: PromotionValues, now: Date = new Date()): PromotionRequest {
  const until = promotionEnd(values, now);
  return {
    title: localized(values.title),
    type: values.type,
    percentOff: values.type === 'PERCENT_OFF' ? Number(values.percentOff) : null,
    bundleQuantity: values.type === 'MULTI_BUY' ? Number(values.bundleQuantity) : null,
    bundlePrice: values.type === 'MULTI_BUY' ? money(values.bundlePrice) : null,
    validFrom: now.toISOString(),
    validUntil: until.toISOString(),
    productIds: values.productIds,
  };
}
