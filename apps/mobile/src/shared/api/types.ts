/**
 * Hand-written types mirroring packages/shared-contracts/openapi.yaml (v0.2.0).
 * openapi-typescript 7.x does not support TypeScript 6 (see VERSIONS.md), so keep these in
 * sync with the contract by hand. Nullable contract fields are `T | null`; optional ones `?`.
 */

// ---------------------------------------------------------------- common
export interface Problem {
  type: string;
  title: string;
  status: number;
  code: string;
  detail?: string;
  instance?: string;
  errors?: { field: string; code: string; message: string }[];
}

export interface Locale {
  code: string;
  nativeName: string;
  direction: 'ltr' | 'rtl';
}

/** Map of locale code to text, e.g. `{ de: 'Äpfel', en: 'Apples', ar: 'تفاح' }`. */
export type LocalizedText = Record<string, string>;

export interface CodeName {
  code: string;
  name: string;
  iconKey?: string | null;
}

export interface Money {
  amountCents: number;
  currency: string;
}

export interface GeoPoint {
  lat: number;
  lng: number;
}

export interface Place {
  label: string;
  street?: string | null;
  houseNumber?: string | null;
  postalCode?: string | null;
  district?: string | null;
  city: string;
  location: GeoPoint;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

// ---------------------------------------------------------------- auth
export type Role = 'CUSTOMER' | 'MERCHANT' | 'ADMIN';
export type AccountType = 'CUSTOMER' | 'MERCHANT';

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  accountType?: AccountType;
  preferredLocale?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  preferredLocale?: string;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: Role[];
  preferredLocale?: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

// ---------------------------------------------------------------- markets
export type FulfilmentType = 'DELIVERY' | 'PICKUP';
export type MarketStatus = 'PENDING_REVIEW' | 'ACTIVE' | 'SUSPENDED';
export type MarketTag = 'ORGANIC' | 'REGIONAL' | 'HALAL' | 'VEGAN_FRIENDLY';

export interface OpeningHours {
  /** 1 = Monday (ISO 8601). */
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
}

export interface FulfilmentOptions {
  delivery: boolean;
  pickup: boolean;
  deliveryRadiusKm?: number | null;
  deliveryFee?: Money | null;
  freeDeliveryFrom?: Money | null;
  minimumOrderValue: Money;
}

export interface MarketSummary {
  id: string;
  slug: string;
  name: string;
  type: CodeName;
  cuisines: CodeName[];
  logoUrl?: string | null;
  coverImageUrl?: string | null;
  location: GeoPoint;
  addressLine: string;
  distanceMeters?: number | null;
  deliversToLocation?: boolean | null;
  openNow: boolean;
  todayHours?: string | null;
  nextOpeningAt?: string | null;
  fulfilment: FulfilmentOptions;
  activePromotionCount: number;
  tags: MarketTag[];
}

export interface MarketDetail extends MarketSummary {
  description?: string | null;
  phone?: string | null;
  openingHours: OpeningHours[];
  primaryLocale: string;
  imprint: { legalName: string; addressLine: string; registerInfo?: string | null };
}

export type MarketSort = 'distance' | 'name' | 'minimum_order' | 'delivery_fee';

export interface ListMarketsParams {
  lat: number;
  lng: number;
  radiusKm?: number;
  types?: readonly string[];
  cuisines?: readonly string[];
  fulfilment?: FulfilmentType;
  openNow?: boolean;
  hasOffers?: boolean;
  q?: string;
  sort?: MarketSort;
  page?: number;
  size?: number;
}

export type BannerTint = 'sage' | 'sky' | 'blush' | 'sand' | 'rose';

export interface Banner {
  id: string;
  overline?: string | null;
  title: string;
  imageUrl?: string | null;
  tint?: BannerTint;
  target: { type: 'CATEGORY' | 'PROMOTIONS' | 'NEW' | 'PRODUCT'; id?: string | null };
}

export interface HomeSection {
  key: 'PROMOTIONS' | 'NEW' | 'FREQUENTLY_ORDERED' | 'CATEGORY';
  title: string;
  subtitle?: string | null;
  categoryId?: string | null;
  items: ProductSummary[];
}

export interface MarketHome {
  market: MarketSummary;
  banners: Banner[];
  sections: HomeSection[];
}

// ---------------------------------------------------------------- catalog
export interface Category {
  id: string;
  parentId?: string | null;
  slug: string;
  name: string;
  iconKey?: string | null;
  imageUrl?: string | null;
  sortOrder: number;
  productCount?: number | null;
}

export type PromotionType = 'PERCENT_OFF' | 'MULTI_BUY';

export interface PromotionBadge {
  promotionId: string;
  label: string;
  type: PromotionType;
}

export interface UnitPrice {
  price: Money;
  unit: 'kg' | 'l' | 'piece';
}

export interface ProductSummary {
  id: string;
  marketId: string;
  catalogProductId?: string;
  name: string;
  brand?: string | null;
  tagline?: string | null;
  unitSize: string;
  imageUrl?: string | null;
  price: Money;
  promotionalPrice?: Money | null;
  comparisonPrice?: UnitPrice | null;
  promotion?: PromotionBadge | null;
  available: boolean;
  organic: boolean;
  isNew: boolean;
  maxOrderQuantity: number;
  favorite?: boolean | null;
}

export interface ProductDetail extends ProductSummary {
  categoryId: string;
  description?: string | null;
  ingredients?: string | null;
  allergens: string[];
  origin?: string | null;
  cuisines: CodeName[];
  barcode?: string | null;
  market: MarketSummary;
}

export type ProductSort = 'relevance' | 'price_asc' | 'price_desc' | 'name';

export interface ListMarketProductsParams {
  q?: string;
  categoryId?: string;
  promotedOnly?: boolean;
  newOnly?: boolean;
  organicOnly?: boolean;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

export interface MarketOffer {
  product: ProductSummary;
  market: MarketSummary;
}

export type OfferSort = 'relevance' | 'price_asc' | 'distance';

export interface SearchProductsParams {
  q: string;
  lat: number;
  lng: number;
  radiusKm?: number;
  sort?: OfferSort;
  page?: number;
  size?: number;
}

export interface Promotion {
  id: string;
  marketId: string;
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

export type FavoriteReason = 'MANUAL' | 'FREQUENTLY_ORDERED';

export interface FavoriteGroup {
  category: Category;
  items: (ProductSummary & { favoriteReason: FavoriteReason })[];
}

// ---------------------------------------------------------------- cart and fulfilment
export interface SetQuantityRequest {
  quantity: number;
}

export interface ReserveFulfilmentRequest {
  type: FulfilmentType;
  slotId: string;
  addressId?: string | null;
}

export interface Slot {
  id: string;
  type: FulfilmentType;
  startsAt: string;
  endsAt: string;
  orderCutoffAt: string;
  fee: Money;
  available: boolean;
  remainingCapacity: number;
}

export interface FulfilmentReservation {
  type: FulfilmentType;
  slot: Slot;
  addressId?: string | null;
  expiresAt: string;
}

export interface CartItem {
  productId: string;
  name: string;
  unitSize: string;
  imageUrl?: string | null;
  unitPrice: Money;
  quantity: number;
  lineSubtotal: Money;
  lineDiscount: Money;
  lineTotal: Money;
  promotion?: PromotionBadge | null;
  available?: boolean;
}

export interface Cart {
  id: string;
  market: MarketSummary;
  items: CartItem[];
  itemCount: number;
  subtotal: Money;
  discountTotal: Money;
  deliveryFee: Money;
  total: Money;
  minimumOrderValue: Money;
  meetsMinimumOrderValue: boolean;
  fulfilment?: FulfilmentReservation | null;
}

export interface CartSummary {
  cartId: string;
  market: MarketSummary;
  itemCount: number;
  total: Money;
  updatedAt: string;
}

// ---------------------------------------------------------------- addresses
export interface AddressRequest {
  label?: string | null;
  street: string;
  houseNumber: string;
  addition?: string | null;
  postalCode: string;
  city: string;
  deliveryInstructions?: string | null;
  location?: GeoPoint | null;
  isDefault?: boolean;
}

export interface Address extends AddressRequest {
  id: string;
  countryCode: 'DE';
  isDefault: boolean;
}

// ---------------------------------------------------------------- orders
export type OrderStatus =
  | 'PLACED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'READY_FOR_PICKUP'
  | 'OUT_FOR_DELIVERY'
  | 'COMPLETED'
  | 'CANCELLED';

export interface OrderItem {
  productId: string;
  name: string;
  unitPrice: Money;
  quantity: number;
  lineTotal: Money;
}

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  placedAt: string;
  market: MarketSummary;
  fulfilmentType: FulfilmentType;
  items: OrderItem[];
  subtotal: Money;
  discountTotal: Money;
  deliveryFee: Money;
  total: Money;
  slot: Slot;
  deliveryAddress?: Address | null;
  customerName?: string | null;
}

// ---------------------------------------------------------------- merchant
export interface MarketProfileRequest {
  name: string;
  typeCode: string;
  cuisineCodes?: string[];
  description?: LocalizedText;
  primaryLocale: string;
  addressLine: string;
  location: GeoPoint;
  phone?: string | null;
  logoUrl?: string | null;
  coverImageUrl?: string | null;
  tags?: MarketTag[];
  fulfilment: FulfilmentOptions;
  openingHours: OpeningHours[];
  legalName: string;
  registerInfo?: string | null;
}

export interface MerchantMarket extends MarketDetail {
  status: MarketStatus;
}

export interface CatalogProduct {
  id: string;
  name: string;
  brand?: string | null;
  unitSize: string;
  imageUrl?: string | null;
  categoryId: string;
  cuisines: CodeName[];
  organic: boolean;
  barcode?: string | null;
  alreadyListed?: boolean | null;
}

export interface SearchCatalogParams {
  /** Own market: the server fills `alreadyListed` for it (404 if not owned). */
  marketId?: string;
  q?: string;
  cuisine?: string;
  categoryId?: string;
  page?: number;
  size?: number;
}

export interface NewCatalogProduct {
  name: LocalizedText;
  description?: LocalizedText;
  tagline?: LocalizedText;
  keywords?: string[];
  brand?: string | null;
  unitSize: string;
  netQuantity?: number | null;
  netUnit?: 'kg' | 'l' | 'piece' | null;
  categoryId: string;
  cuisineCodes?: string[];
  organic?: boolean;
  allergens?: string[];
  imageUrl?: string | null;
  barcode?: string | null;
}

export interface CreateListingRequest {
  catalogProductId?: string | null;
  newProduct?: NewCatalogProduct | null;
  price: Money;
  available?: boolean;
  maxOrderQuantity?: number;
}

export interface UpdateListingRequest {
  price?: Money;
  available?: boolean;
  maxOrderQuantity?: number;
}

export interface Listing extends ProductSummary {
  privateProduct: boolean;
}

export interface PromotionRequest {
  title: LocalizedText;
  description?: LocalizedText;
  type: PromotionType;
  percentOff?: number | null;
  bundleQuantity?: number | null;
  bundlePrice?: Money | null;
  validFrom: string;
  validUntil: string;
  productIds: string[];
}
