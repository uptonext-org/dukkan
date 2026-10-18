/**
 * Hand-written TypeScript mirror of packages/shared-contracts/openapi.yaml (v0.1.0).
 *
 * openapi-typescript 7.13.0 declares `typescript: ^5.x` as a peer and therefore
 * cannot be installed next to the mandated TypeScript 6.0.3, so these types are
 * maintained by hand. Keep them in sync with the contract.
 */

export type Uuid = string;
/** ISO 8601 timestamp with offset, e.g. `2026-10-19T08:00:00+02:00`. */
export type IsoDateTime = string;

export interface Money {
  /** Integer amount in minor units (cents). */
  amountCents: number;
  /** ISO 4217 currency code, e.g. `EUR`. */
  currency: string;
}

export interface ProblemFieldError {
  field: string;
  message: string;
}

/** RFC 9457 problem details with an extra machine-readable `code`. */
export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  errors?: ProblemFieldError[];
}

export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'INVALID_CREDENTIALS'
  | 'NOT_FOUND'
  | 'EMAIL_TAKEN'
  | 'SLOT_FULL'
  | 'PRODUCT_UNAVAILABLE'
  | 'MAX_QUANTITY_EXCEEDED'
  | 'ADDRESS_NOT_SERVICEABLE'
  | 'SLOT_CUTOFF_PASSED'
  | 'SLOT_NOT_RESERVED'
  | 'CART_EMPTY'
  | 'MINIMUM_ORDER_NOT_MET'
  | 'ORDER_NOT_CANCELLABLE';

export type PostalCode = string;

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface User {
  id: Uuid;
  email: string;
  firstName: string;
  lastName: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds until expiry. */
  expiresIn: number;
  user: User;
}

export interface Category {
  id: Uuid;
  parentId?: Uuid | null;
  slug: string;
  name: string;
  iconKey?: string | null;
  sortOrder: number;
}

export type PromotionType = 'PERCENT_OFF' | 'MULTI_BUY';

export interface PromotionBadge {
  promotionId: Uuid;
  label: string;
  type: PromotionType;
}

export interface UnitPrice {
  price: Money;
  unit: 'kg' | 'l' | 'piece';
}

export interface ProductSummary {
  id: Uuid;
  sku: string;
  name: string;
  brand?: string | null;
  unitSize: string;
  imageUrl?: string | null;
  price: Money;
  /** Price per reference unit (Grundpreis). */
  comparisonPrice?: UnitPrice | null;
  promotion?: PromotionBadge | null;
  available: boolean;
}

export interface ProductDetail extends ProductSummary {
  categoryId: Uuid;
  description?: string | null;
  ingredients?: string | null;
  allergens: string[];
  origin?: string | null;
  maxOrderQuantity: number;
}

export interface ProductPage {
  items: ProductSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type ProductSort = 'relevance' | 'price_asc' | 'price_desc' | 'name';

export interface ListProductsParams {
  categoryId?: Uuid | undefined;
  q?: string | undefined;
  promotedOnly?: boolean | undefined;
  sort?: ProductSort | undefined;
  page?: number | undefined;
  size?: number | undefined;
}

export interface Promotion {
  id: Uuid;
  title: string;
  description?: string | null;
  type: PromotionType;
  percentOff?: number | null;
  bundleQuantity?: number | null;
  bundlePrice?: Money | null;
  validFrom: IsoDateTime;
  validUntil: IsoDateTime;
  productIds: Uuid[];
}

export interface SetQuantityRequest {
  quantity: number;
}

export interface ReserveSlotRequest {
  slotId: Uuid;
  addressId: Uuid;
}

export interface CartItem {
  productId: Uuid;
  name: string;
  unitSize: string;
  imageUrl?: string | null;
  unitPrice: Money;
  quantity: number;
  lineSubtotal: Money;
  lineDiscount: Money;
  lineTotal: Money;
  promotion?: PromotionBadge | null;
}

export interface DeliverySlot {
  id: Uuid;
  startsAt: IsoDateTime;
  endsAt: IsoDateTime;
  orderCutoffAt: IsoDateTime;
  fee: Money;
  /** False when full or past cutoff. */
  available: boolean;
  remainingCapacity: number;
}

export interface DeliveryReservation {
  slot: DeliverySlot;
  addressId: Uuid;
  expiresAt: IsoDateTime;
}

export interface Cart {
  id: Uuid;
  items: CartItem[];
  /** Sum of quantities. */
  itemCount: number;
  subtotal: Money;
  discountTotal: Money;
  deliveryFee: Money;
  total: Money;
  minimumOrderValue: Money;
  meetsMinimumOrderValue: boolean;
  delivery?: DeliveryReservation | null;
}

export interface AddressRequest {
  label?: string | null;
  street: string;
  houseNumber: string;
  addition?: string | null;
  postalCode: PostalCode;
  city: string;
  deliveryInstructions?: string | null;
  isDefault?: boolean;
}

export interface Address extends AddressRequest {
  id: Uuid;
  countryCode: 'DE';
  isDefault: boolean;
  serviceable: boolean;
}

export interface DeliveryAreaCheck {
  postalCode: PostalCode;
  serviceable: boolean;
  hubName?: string | null;
}

export type OrderStatus = 'PLACED' | 'CANCELLED' | 'OUT_FOR_DELIVERY' | 'DELIVERED';

export interface OrderItem {
  productId: Uuid;
  name: string;
  unitPrice: Money;
  quantity: number;
  lineTotal: Money;
}

export interface Order {
  id: Uuid;
  orderNumber: string;
  status: OrderStatus;
  placedAt: IsoDateTime;
  items: OrderItem[];
  subtotal: Money;
  discountTotal: Money;
  deliveryFee: Money;
  total: Money;
  slot: DeliverySlot;
  address: Address;
}
