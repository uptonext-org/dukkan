import type { ApiClient } from './client';
import type {
  Address,
  AddressRequest,
  AuthResponse,
  Cart,
  Category,
  DeliveryAreaCheck,
  DeliverySlot,
  ListProductsParams,
  LoginRequest,
  Order,
  ProductDetail,
  ProductPage,
  Promotion,
  RegisterRequest,
  ReserveSlotRequest,
  User,
} from './types';

type Signal = AbortSignal | undefined;

const enc = encodeURIComponent;

/** Typed operations of the Dukkan backend (one method per OpenAPI operationId). */
export function createDukkanApi(client: ApiClient) {
  return {
    register: (body: RegisterRequest) =>
      client.request<AuthResponse>({ method: 'POST', path: '/auth/register', body }),
    login: (body: LoginRequest) =>
      client.request<AuthResponse>({ method: 'POST', path: '/auth/login', body }),
    getCurrentUser: (signal?: Signal) => client.request<User>({ method: 'GET', path: '/me', signal }),

    listCategories: (signal?: Signal) =>
      client.request<Category[]>({ method: 'GET', path: '/categories', signal }),
    listProducts: (params: ListProductsParams, signal?: Signal) =>
      client.request<ProductPage>({ method: 'GET', path: '/products', query: { ...params }, signal }),
    getProduct: (productId: string, signal?: Signal) =>
      client.request<ProductDetail>({ method: 'GET', path: `/products/${enc(productId)}`, signal }),
    searchSuggestions: (q: string, limit?: number, signal?: Signal) =>
      client.request<string[]>({ method: 'GET', path: '/search/suggestions', query: { q, limit }, signal }),

    listPromotions: (signal?: Signal) =>
      client.request<Promotion[]>({ method: 'GET', path: '/promotions', signal }),

    getCart: (signal?: Signal) => client.request<Cart>({ method: 'GET', path: '/cart', signal }),
    clearCart: () => client.request<undefined>({ method: 'DELETE', path: '/cart' }),
    setCartItemQuantity: (productId: string, quantity: number) =>
      client.request<Cart>({ method: 'PUT', path: `/cart/items/${enc(productId)}`, body: { quantity } }),
    removeCartItem: (productId: string) =>
      client.request<Cart>({ method: 'DELETE', path: `/cart/items/${enc(productId)}` }),
    reserveDeliverySlot: (body: ReserveSlotRequest) =>
      client.request<Cart>({ method: 'PUT', path: '/cart/delivery', body }),

    listAddresses: (signal?: Signal) =>
      client.request<Address[]>({ method: 'GET', path: '/addresses', signal }),
    createAddress: (body: AddressRequest) =>
      client.request<Address>({ method: 'POST', path: '/addresses', body }),
    updateAddress: (addressId: string, body: AddressRequest) =>
      client.request<Address>({ method: 'PUT', path: `/addresses/${enc(addressId)}`, body }),
    deleteAddress: (addressId: string) =>
      client.request<undefined>({ method: 'DELETE', path: `/addresses/${enc(addressId)}` }),

    checkDeliveryArea: (postalCode: string, signal?: Signal) =>
      client.request<DeliveryAreaCheck>({
        method: 'GET',
        path: '/delivery-areas/check',
        query: { postalCode },
        signal,
      }),
    listDeliverySlots: (addressId: string, signal?: Signal) =>
      client.request<DeliverySlot[]>({
        method: 'GET',
        path: '/delivery-slots',
        query: { addressId },
        signal,
      }),

    listOrders: (signal?: Signal) => client.request<Order[]>({ method: 'GET', path: '/orders', signal }),
    placeOrder: (idempotencyKey: string) =>
      client.request<Order>({
        method: 'POST',
        path: '/orders',
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    getOrder: (orderId: string, signal?: Signal) =>
      client.request<Order>({ method: 'GET', path: `/orders/${enc(orderId)}`, signal }),
    cancelOrder: (orderId: string) =>
      client.request<Order>({ method: 'POST', path: `/orders/${enc(orderId)}/cancel` }),
  };
}

export type DukkanApi = ReturnType<typeof createDukkanApi>;
