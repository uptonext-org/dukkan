/** Hand-written subset of packages/shared-contracts/openapi.yaml used by the portal. */

export interface Problem {
  status: number;
  code: string;
  detail?: string;
  errors?: { field: string; code: string; message: string }[];
}

export type Role = 'CUSTOMER' | 'MERCHANT' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: Role[];
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
  user: User;
}

export interface Money {
  amountCents: number;
  currency: string;
}

export type MarketStatus = 'PENDING_REVIEW' | 'ACTIVE' | 'SUSPENDED';

export interface CodeName {
  code: string;
  name: string;
}

export interface GeoPoint {
  lat: number;
  lng: number;
}

export interface FulfilmentOptions {
  delivery: boolean;
  pickup: boolean;
  deliveryRadiusKm?: number | null;
  deliveryFee?: Money | null;
  freeDeliveryFrom?: Money | null;
  minimumOrderValue: Money;
}

export interface OpeningHours {
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
}

export type MarketTag = 'ORGANIC' | 'REGIONAL' | 'HALAL' | 'VEGAN_FRIENDLY';

export interface Place {
  label: string;
  city: string;
  location: GeoPoint;
}

export interface MarketProfileRequest {
  name: string;
  typeCode: string;
  cuisineCodes: string[];
  description?: Record<string, string>;
  primaryLocale: string;
  addressLine: string;
  location: GeoPoint;
  phone: string | null;
  logoUrl: string | null;
  coverImageUrl: string | null;
  tags: MarketTag[];
  fulfilment: FulfilmentOptions;
  openingHours: OpeningHours[];
  legalName: string;
  registerInfo: string | null;
}

export type PaymentMethod = 'CASH' | 'CARD';

export type FeatureKey = 'SHOPPING_LIST' | 'SHOPPING_LIST_VOICE' | 'SHOPPING_LIST_MEDIA' | 'MERCHANT_MULTILINGUAL';

/** Functions platform admins switch on and off for all customers. */
export type FeatureSwitches = Record<FeatureKey, boolean>;

/** What a merchant controls about ordering; null numbers use the platform default. */
export interface OrderSettings {
  ordersEnabled: boolean;
  pausedUntil?: string | null;
  closedFrom?: string | null;
  closedUntil?: string | null;
  deliveryCapacity?: number | null;
  pickupCapacity?: number | null;
  deliveryLeadMinutes?: number | null;
  pickupLeadMinutes?: number | null;
  daysAhead?: number | null;
  deliveryHours: OpeningHours[];
  autoAccept: boolean;
  paymentMethods: PaymentMethod[];
  defaultMaxOrderQuantity?: number | null;
}

export interface MerchantOrderSettings extends OrderSettings {
  defaults: {
    deliveryCapacity: number;
    pickupCapacity: number;
    deliveryLeadMinutes: number;
    pickupLeadMinutes: number;
    daysAhead: number;
    maxOrderQuantity: number;
  };
}

export interface MerchantMarket {
  id: string;
  slug: string;
  name: string;
  status: MarketStatus;
  primaryLocale: string;
  cuisines?: CodeName[];
  description?: string | null;
  descriptions?: Record<string, string>;
  phone?: string | null;
  logoUrl?: string | null;
  coverImageUrl?: string | null;
  location?: GeoPoint;
  tags?: MarketTag[];
  fulfilment?: FulfilmentOptions;
  openingHours?: OpeningHours[];
  addressLine: string;
  type: { code: string; name: string };
  imprint: { legalName: string; addressLine: string; registerInfo?: string | null };
  review?: MarketReview;
}

export type MarketReviewCode = 'LEGAL_NAME' | 'POSTAL_ADDRESS' | 'PHONE' | 'REGISTER_INFO';

/** Seller information checklist; approval needs every required item. */
export interface MarketReview {
  complete: boolean;
  items: { code: MarketReviewCode; ok: boolean; required: boolean }[];
}

export interface Listing {
  id: string;
  name: string;
  brand?: string | null;
  unitSize: string;
  price: Money;
  available: boolean;
  maxOrderQuantity: number;
  privateProduct: boolean;
  /** The market's own details; absent when the catalog values show. */
  overrides?: ListingOverrides | null;
}

/** One feedback message as the merchant sees it (customer's first name only). */
export interface MarketFeedback {
  id: string;
  customerName: string;
  rating?: number | null;
  message: string;
  createdAt: string;
}

export interface MerchantFeedbackPage {
  enabled: boolean;
  feedback: PageResponse<MarketFeedback>;
}

/** A market's own details of a catalog product; empty fields show the catalog value. */
export interface ListingOverrides {
  name?: string | null;
  description?: string | null;
  unitSize?: string | null;
  brand?: string | null;
}

/** Report of the shared catalog import (admins). */
export interface CatalogImportResult {
  dryRun: boolean;
  applied: boolean;
  created: number;
  existing: number;
  failed: number;
  rows: { line: number; action: 'CREATE' | 'EXISTS' | 'ERROR'; name?: string | null; productId?: string | null; errors: { field: string; code: string; message: string }[] }[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type OrderStatus = 'PLACED' | 'ACCEPTED' | 'REJECTED' | 'READY_FOR_PICKUP' | 'OUT_FOR_DELIVERY' | 'COMPLETED' | 'CANCELLED';
export type FulfilmentType = 'DELIVERY' | 'PICKUP';

export interface OrderItem {
  lineNo: number;
  productId: string;
  name: string;
  quantity: number;
  /** Units handed over when fewer than ordered (missing items); null = as ordered. */
  fulfilledQuantity?: number | null;
  /** Charged amount of the line. */
  lineTotal: Money;
}

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  placedAt: string;
  fulfilmentType: FulfilmentType;
  items: OrderItem[];
  total: Money;
  /** The total when ordered, once missing items were entered; null otherwise. */
  originalTotal?: Money | null;
  slot: { startsAt: string; endsAt: string };
  customerName?: string | null;
}

export type ImportAction = 'CREATE' | 'LIST' | 'UPDATE' | 'ERROR';

export interface ImportRow {
  line: number;
  action: ImportAction;
  name?: string | null;
  listingId?: string | null;
  errors: { field: string; code: string; message: string }[];
}

export interface ImportResult {
  dryRun: boolean;
  applied: boolean;
  created: number;
  listed: number;
  updated: number;
  failed: number;
  rows: ImportRow[];
}

export type ReviewStatus = 'PENDING' | 'KEPT_PRIVATE' | 'PROMOTED';

export interface CatalogReviewItem {
  id: string;
  market?: { id: string; name: string } | null;
  category?: { id: string; name: string } | null;
  brand?: string | null;
  unitSize: string;
  barcode?: string | null;
  imageUrl?: string | null;
  reviewStatus: ReviewStatus;
  createdAt: string;
  texts: Record<string, { name: string; description?: string | null; machineTranslated: boolean }>;
  duplicates: { id: string; name: string; unitSize: string; barcode?: string | null }[];
}

export type PromotionType = 'PERCENT_OFF' | 'MULTI_BUY' | 'FIXED_PRICE';

/** `quantity` units together for `price` (MULTI_BUY). */
export interface PromotionTier {
  quantity: number;
  price: Money;
}

/** The offer price of one product (FIXED_PRICE). */
export interface PromotionOffer {
  productId: string;
  offerPrice: Money;
}

export interface Promotion {
  id: string;
  title: string;
  description?: string | null;
  type: PromotionType;
  percentOff?: number | null;
  bundleQuantity?: number | null;
  bundlePrice?: Money | null;
  tiers?: PromotionTier[];
  offers?: PromotionOffer[];
  validFrom: string;
  validUntil: string;
  /** ISO weekdays (1 = Monday); null = every day. */
  weekdays?: number[] | null;
  /** "HH:mm" Berlin time; null = all day. */
  dailyFrom?: string | null;
  dailyUntil?: string | null;
  productIds: string[];
}

export interface PromotionRequest {
  title: Record<string, string>;
  type: PromotionType;
  percentOff: number | null;
  bundleQuantity: number | null;
  bundlePrice: Money | null;
  tiers: PromotionTier[] | null;
  offers: PromotionOffer[] | null;
  validFrom: string;
  validUntil: string;
  weekdays: number[] | null;
  dailyFrom: string | null;
  dailyUntil: string | null;
  productIds: string[];
}

export interface TranslationResult {
  fields: { key: string; values: Record<string, string> }[];
}

export interface InsightTotals {
  orders: number;
  revenue: Money;
  averageOrderValue: Money | null;
  discounts: Money;
}

export interface MarketInsights {
  from: string;
  to: string;
  totals: InsightTotals;
  previous: InsightTotals;
  cancelledOrders: number;
  rejectedOrders: number;
  pickupOrders: number;
  deliveryOrders: number;
  customers: number;
  returningCustomers: number;
  daily: { date: string; orders: number; revenue: Money }[];
  ordersByHour: number[];
  topProducts: { listingId: string; name: string; unitSize?: string | null; imageUrl?: string | null; quantity: number; revenue: Money }[];
}
