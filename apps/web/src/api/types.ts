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

export interface MerchantMarket {
  id: string;
  slug: string;
  name: string;
  status: MarketStatus;
  primaryLocale: string;
  addressLine: string;
  type: { code: string; name: string };
  imprint: { legalName: string; addressLine: string; registerInfo?: string | null };
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

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  placedAt: string;
  fulfilmentType: FulfilmentType;
  items: { productId: string; name: string; quantity: number; lineTotal: Money }[];
  total: Money;
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

export type PromotionType = 'PERCENT_OFF' | 'MULTI_BUY';

export interface Promotion {
  id: string;
  title: string;
  description?: string | null;
  type: PromotionType;
  percentOff?: number | null;
  bundleQuantity?: number | null;
  bundlePrice?: Money | null;
  validFrom: string;
  validUntil: string;
  productIds: string[];
}

export interface PromotionRequest {
  title: Record<string, string>;
  type: PromotionType;
  percentOff: number | null;
  bundleQuantity: number | null;
  bundlePrice: Money | null;
  validFrom: string;
  validUntil: string;
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
