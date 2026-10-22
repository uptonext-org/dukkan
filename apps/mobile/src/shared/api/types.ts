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
  /** Whether the email address is confirmed (B-39b); older servers omit it. */
  emailVerified?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  /** Single use; exchange it at /auth/refresh. Stored in the Keychain only. */
  refreshToken: string;
  refreshExpiresIn: number;
  user: User;
}

export type SocialProvider = 'APPLE' | 'GOOGLE';

export type DevicePlatform = 'IOS' | 'ANDROID';

export type NotificationKind =
  | 'NEW_ORDER'
  | 'ORDER_CANCELLED'
  | 'ORDER_ACCEPTED'
  | 'ORDER_REJECTED'
  | 'ORDER_READY_FOR_PICKUP'
  | 'ORDER_OUT_FOR_DELIVERY'
  | 'ORDER_COMPLETED';

/** Inbox entry, written in the recipient's language by the server. */
export interface AppNotification {
  id: string;
  kind: NotificationKind;
  title: string;
  body: string;
  orderId?: string | null;
  marketId?: string | null;
  createdAt: string;
  read: boolean;
}

/** Push messages on or off; the inbox always fills. */
export interface NotificationSettings {
  pushEnabled: boolean;
}

export interface NotificationPage extends Page<AppNotification> {
  unreadCount: number;
}

export interface SocialSignInRequest {
  provider: SocialProvider;
  idToken: string;
  nonce?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  accountType?: AccountType | null;
  preferredLocale?: string | null;
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
  key: 'PROMOTIONS' | 'BESTSELLERS' | 'NEW' | 'FREQUENTLY_ORDERED' | 'CATEGORY';
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
  /** Placeholder hint while there is no photo (contract ProductSummary.iconKey). */
  iconKey?: string | null;
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
  /** The market's best sellers, most sold first; other filters and the sort are ignored. */
  bestsellersOnly?: boolean;
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
  /** Placeholder hint while there is no photo (contract ProductSummary.iconKey). */
  iconKey?: string | null;
  unitPrice: Money;
  quantity: number;
  lineSubtotal: Money;
  lineDiscount: Money;
  lineTotal: Money;
  promotion?: PromotionBadge | null;
  available?: boolean;
  maxOrderQuantity: number;
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

export type ReorderSkipReason = 'REMOVED' | 'UNAVAILABLE' | 'LIMITED';

export interface ReorderResult {
  marketId: string;
  addedProducts: number;
  skipped: { productId: string; name: string; reason: ReorderSkipReason }[];
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
  /** The description in every language; older servers omit it. */
  descriptions?: LocalizedText;
}

export interface CatalogProduct {
  id: string;
  name: string;
  brand?: string | null;
  unitSize: string;
  imageUrl?: string | null;
  /** Placeholder hint while there is no photo (contract ProductSummary.iconKey). */
  iconKey?: string | null;
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
  /** Absent = unchanged, null = remove the market's photo (a shared catalog image shows again). */
  imageUrl?: string | null;
}

export type MediaPurpose = 'LOGO' | 'COVER' | 'BANNER' | 'PRODUCT';

export type MediaContentType = 'image/jpeg' | 'image/png' | 'image/webp';

/** Response of `POST /merchant/markets/{marketId}/media`. */
export interface MediaUpload {
  key: string;
  /** Absolute URL; put it into logoUrl, coverImageUrl, newProduct.imageUrl or UpdateListingRequest.imageUrl. */
  url: string;
  width: number;
  height: number;
  contentType: MediaContentType;
  sizeBytes: number;
}

/**
 * The file part of an upload: a local file URI (React Native's FormData reads it natively) or a
 * Blob (tests and other JS runtimes).
 */
export type UploadFile = { uri: string; type: string; name: string } | { blob: Blob; name: string };

/** `POST /merchant/markets/{marketId}/product-drafts`: at least one of both. */
// ---------------------------------------------------------------- machine translation (B-38)
export type TranslationKind = 'PRODUCT_NAME' | 'PRODUCT_DESCRIPTION' | 'PROMOTION' | 'MARKET_DESCRIPTION';

export interface TranslationField {
  /** Caller-chosen id, echoed in the result. */
  key: string;
  /** Locale code → text; empty values are translated. */
  values: LocalizedText;
}

export interface TranslationRequest {
  kind: TranslationKind;
  fields: TranslationField[];
}

export interface TranslationResult {
  /** Only fields with new suggestions, and only the newly translated locales. */
  fields: TranslationField[];
}

export interface ProductDraftRequest {
  text?: string | null;
  /** `key` of an uploadMedia result. */
  imageKey?: string | null;
}

export type ProductDraftWarning =
  | 'AI_NOT_CONFIGURED'
  | 'AI_UNAVAILABLE'
  | 'AI_DECLINED'
  | 'IMAGE_NOT_ANALYSED'
  | 'NAME_MISSING'
  | 'CATEGORY_UNCERTAIN'
  | 'UNIT_SIZE_MISSING'
  | 'PRICE_MISSING';

/** Like NewCatalogProduct, but every field may still be missing. */
export interface ProductDraftProduct {
  name: LocalizedText;
  description: LocalizedText;
  keywords: string[];
  brand?: string | null;
  unitSize?: string | null;
  netQuantity?: number | null;
  netUnit?: 'kg' | 'l' | 'piece' | null;
  categoryId?: string | null;
  cuisineCodes: string[];
  organic: boolean;
  allergens: string[];
  imageUrl?: string | null;
  barcode?: string | null;
}

/** A product draft from text and/or a photo; nothing is saved until createListing. */
export interface ProductDraft {
  engine: 'AI' | 'RULES';
  product: ProductDraftProduct;
  price?: Money | null;
  /** Shared catalog products that look like the same product, best first. */
  catalogMatches: CatalogProduct[];
  warnings: ProductDraftWarning[];
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
