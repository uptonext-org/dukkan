import type { ApiClient } from './client';
import type {
  Address,
  AddressRequest,
  AuthResponse,
  Cart,
  CartSummary,
  CatalogProduct,
  Category,
  CodeName,
  CreateListingRequest,
  DevicePlatform,
  FavoriteGroup,
  FulfilmentType,
  Listing,
  ListMarketProductsParams,
  ListMarketsParams,
  Locale,
  LoginRequest,
  MarketDetail,
  MarketHome,
  MarketOffer,
  MarketProfileRequest,
  MarketSummary,
  MediaPurpose,
  MediaUpload,
  MerchantMarket,
  NotificationPage,
  NotificationSettings,
  Order,
  OrderStatus,
  Page,
  Place,
  ProductDetail,
  ProductDraft,
  ProductDraftRequest,
  ProductSummary,
  Promotion,
  PromotionRequest,
  RegisterRequest,
  ReorderResult,
  ReserveFulfilmentRequest,
  SearchCatalogParams,
  SearchProductsParams,
  Slot,
  SocialSignInRequest,
  TranslationRequest,
  TranslationResult,
  ShoppingListCapabilities,
  ShoppingListRequest,
  ShoppingListResult,
  UpdateListingRequest,
  UpdateUserRequest,
  UploadFile,
  User,
} from './types';

type Signal = AbortSignal | undefined;

const enc = encodeURIComponent;

/**
 * Typed facade over every operation in the v0.2 contract (operationId in the comments).
 * `Accept-Language` and `Authorization` are added by the ApiClient.
 */
export interface DukkanApi {
  // meta
  listLocales(signal?: Signal): Promise<Locale[]>;
  listMarketTypes(signal?: Signal): Promise<CodeName[]>;
  listCuisines(signal?: Signal): Promise<CodeName[]>;
  // auth
  register(request: RegisterRequest): Promise<AuthResponse>;
  login(request: LoginRequest): Promise<AuthResponse>;
  /** Rotates the refresh token; the old one is spent. */
  refreshSession(refreshToken: string): Promise<AuthResponse>;
  logout(refreshToken: string): Promise<void>;
  /** Always resolves (202), whether or not the account exists. */
  requestPasswordReset(email: string): Promise<void>;
  confirmPasswordReset(token: string, newPassword: string): Promise<void>;
  verifyEmail(token: string): Promise<void>;
  resendEmailVerification(): Promise<void>;
  changePassword(currentPassword: string, newPassword: string): Promise<void>;
  /** GDPR export (B-51): everything stored about the user, as returned by the server. */
  exportMyData(): Promise<unknown>;
  /** Account deletion; `password` is null for Apple/Google-only accounts. */
  deleteMyAccount(password: string | null): Promise<void>;
  socialSignIn(request: SocialSignInRequest): Promise<AuthResponse>;
  // notifications (B-37)
  listNotifications(signal?: Signal): Promise<NotificationPage>;
  markNotificationsRead(): Promise<void>;
  getNotificationSettings(signal?: Signal): Promise<NotificationSettings>;
  updateNotificationSettings(settings: NotificationSettings): Promise<NotificationSettings>;
  registerDevice(token: string, platform: DevicePlatform): Promise<void>;
  unregisterDevice(token: string): Promise<void>;
  getCurrentUser(signal?: Signal): Promise<User>;
  updateCurrentUser(request: UpdateUserRequest): Promise<User>;
  // geo
  searchPlaces(q: string, limit?: number, signal?: Signal): Promise<Place[]>;
  reverseGeocode(lat: number, lng: number, signal?: Signal): Promise<Place>;
  // markets
  listMarkets(params: ListMarketsParams, signal?: Signal): Promise<Page<MarketSummary>>;
  getMarket(marketId: string, at?: { lat: number; lng: number }, signal?: Signal): Promise<MarketDetail>;
  getMarketHome(marketId: string, signal?: Signal): Promise<MarketHome>;
  listMarketCategories(marketId: string, signal?: Signal): Promise<Category[]>;
  listCategories(signal?: Signal): Promise<Category[]>;
  listMarketProducts(
    marketId: string,
    params: ListMarketProductsParams,
    signal?: Signal,
  ): Promise<Page<ProductSummary>>;
  marketSearchSuggestions(marketId: string, q: string, signal?: Signal): Promise<string[]>;
  listMarketPromotions(marketId: string, signal?: Signal): Promise<Promotion[]>;
  getProduct(productId: string, signal?: Signal): Promise<ProductDetail>;
  searchProductsNearby(params: SearchProductsParams, signal?: Signal): Promise<Page<MarketOffer>>;
  // favorites
  listFavoriteMarkets(at?: { lat: number; lng: number }, signal?: Signal): Promise<MarketSummary[]>;
  addFavoriteMarket(marketId: string): Promise<void>;
  removeFavoriteMarket(marketId: string): Promise<void>;
  listMarketFavorites(marketId: string, signal?: Signal): Promise<FavoriteGroup[]>;
  addFavoriteProduct(productId: string): Promise<void>;
  removeFavoriteProduct(productId: string): Promise<void>;
  // cart
  listCarts(signal?: Signal): Promise<CartSummary[]>;
  getCart(marketId: string, signal?: Signal): Promise<Cart>;
  clearCart(marketId: string): Promise<void>;
  setCartItemQuantity(marketId: string, productId: string, quantity: number): Promise<Cart>;
  removeCartItem(marketId: string, productId: string): Promise<Cart>;
  reserveFulfilment(marketId: string, request: ReserveFulfilmentRequest): Promise<Cart>;
  listSlots(marketId: string, type: FulfilmentType, addressId?: string, signal?: Signal): Promise<Slot[]>;
  // addresses
  listAddresses(signal?: Signal): Promise<Address[]>;
  createAddress(request: AddressRequest): Promise<Address>;
  updateAddress(addressId: string, request: AddressRequest): Promise<Address>;
  deleteAddress(addressId: string): Promise<void>;
  // orders
  placeOrder(marketId: string, idempotencyKey: string): Promise<Order>;
  listOrders(signal?: Signal): Promise<Order[]>;
  getOrder(orderId: string, signal?: Signal): Promise<Order>;
  cancelOrder(orderId: string): Promise<Order>;
  /** Puts the products of a past order into its market's cart again (skipped ones are reported). */
  reorder(orderId: string): Promise<ReorderResult>;
  // merchant
  listMyMarkets(signal?: Signal): Promise<MerchantMarket[]>;
  createMyMarket(request: MarketProfileRequest): Promise<MerchantMarket>;
  updateMyMarket(marketId: string, request: MarketProfileRequest): Promise<MerchantMarket>;
  searchSharedCatalog(params: SearchCatalogParams, signal?: Signal): Promise<Page<CatalogProduct>>;
  listMyListings(
    marketId: string,
    params: { q?: string; page?: number; size?: number },
    signal?: Signal,
  ): Promise<Page<Listing>>;
  createListing(marketId: string, request: CreateListingRequest): Promise<Listing>;
  updateListing(marketId: string, productId: string, request: UpdateListingRequest): Promise<Listing>;
  deleteListing(marketId: string, productId: string): Promise<void>;
  listMyPromotions(marketId: string, signal?: Signal): Promise<Promotion[]>;
  createPromotion(marketId: string, request: PromotionRequest): Promise<Promotion>;
  deletePromotion(marketId: string, promotionId: string): Promise<void>;
  listMarketOrders(marketId: string, status?: OrderStatus, signal?: Signal): Promise<Order[]>;
  advanceOrderStatus(orderId: string, status: OrderStatus): Promise<Order>;
  /** Multipart upload of a JPEG, PNG or WebP photo (≤ 5 MB) for one of my markets. */
  uploadMedia(marketId: string, file: UploadFile, purpose: MediaPurpose): Promise<MediaUpload>;
  /** AI product capture: a draft from text and/or an uploaded photo (nothing is saved). */
  createProductDraft(marketId: string, request: ProductDraftRequest): Promise<ProductDraft>;
  translateTexts(request: TranslationRequest): Promise<TranslationResult>;
  getShoppingListCapabilities(signal?: Signal): Promise<ShoppingListCapabilities>;
  /** Shopping list by voice, photo or video: matched products to confirm (nothing is added yet). */
  readShoppingList(marketId: string, request: ShoppingListRequest): Promise<ShoppingListResult>;
}

/** Uploads may take longer than ordinary requests on mobile networks. */
export const UPLOAD_TIMEOUT_MS = 60_000;

/** The server may wait for a language model (30 s per attempt, one retry) before answering. */
export const DRAFT_TIMEOUT_MS = 75_000;

export function mediaForm(file: UploadFile, purpose: MediaPurpose): FormData {
  const form = new FormData();
  form.append('purpose', purpose);
  if ('blob' in file) {
    // The three-argument form (file name) exists at runtime; React Native's typings only declare two.
    (form as unknown as { append(name: string, value: Blob, fileName: string): void }).append('file', file.blob, file.name);
  } else {
    // React Native's FormData sends { uri, type, name } as a file part read from the device.
    form.append('file', { uri: file.uri, type: file.type, name: file.name });
  }
  return form;
}

export function shoppingListForm(request: ShoppingListRequest): FormData {
  const form = new FormData();
  if (request.text !== undefined && request.text.trim() !== '') {
    form.append('text', request.text.trim());
  }
  for (const file of request.images) {
    if ('blob' in file) {
      (form as unknown as { append(name: string, value: Blob, fileName: string): void }).append('images', file.blob, file.name);
    } else {
      form.append('images', { uri: file.uri, type: file.type, name: file.name });
    }
  }
  return form;
}

export function createDukkanApi(client: ApiClient): DukkanApi {
  const get = <T>(path: string, query?: Record<string, unknown>, signal?: Signal) =>
    client.request<T>({
      method: 'GET',
      path,
      query: query as Parameters<ApiClient['request']>[0]['query'],
      signal,
    });

  return {
    listLocales: (signal) => get('/locales', undefined, signal),
    listMarketTypes: (signal) => get('/market-types', undefined, signal),
    listCuisines: (signal) => get('/cuisines', undefined, signal),

    register: (body) => client.request({ method: 'POST', path: '/auth/register', body, anonymous: true }),
    login: (body) => client.request({ method: 'POST', path: '/auth/login', body, anonymous: true }),
    refreshSession: (refreshToken) => client.request({ method: 'POST', path: '/auth/refresh', body: { refreshToken }, anonymous: true }),
    logout: (refreshToken) => client.request({ method: 'POST', path: '/auth/logout', body: { refreshToken }, anonymous: true }),
    requestPasswordReset: (email) => client.request({ method: 'POST', path: '/auth/password-reset', body: { email }, anonymous: true }),
    verifyEmail: (token) => client.request({ method: 'POST', path: '/auth/verify-email', body: { token }, anonymous: true }),
    resendEmailVerification: () => client.request({ method: 'POST', path: '/me/email-verification' }),
    confirmPasswordReset: (token, newPassword) =>
      client.request({ method: 'POST', path: '/auth/password-reset/confirm', body: { token, newPassword }, anonymous: true }),
    changePassword: (currentPassword, newPassword) => client.request({ method: 'PUT', path: '/me/password', body: { currentPassword, newPassword } }),
    exportMyData: () => client.request({ method: 'GET', path: '/me/export' }),
    deleteMyAccount: (password) => client.request({ method: 'DELETE', path: '/me', body: password === null ? {} : { password } }),
    socialSignIn: (body) => client.request({ method: 'POST', path: '/auth/social', body, anonymous: true }),
    listNotifications: (signal) => get('/me/notifications', { size: 50 }, signal),
    markNotificationsRead: () => client.request({ method: 'POST', path: '/me/notifications/read' }),
    getNotificationSettings: (signal) => get('/me/notification-settings', undefined, signal),
    updateNotificationSettings: (body) => client.request({ method: 'PUT', path: '/me/notification-settings', body }),
    registerDevice: (token, platform) => client.request({ method: 'PUT', path: '/me/devices', body: { token, platform } }),
    unregisterDevice: (token) => client.request({ method: 'DELETE', path: '/me/devices', body: { token } }),
    getCurrentUser: (signal) => get('/me', undefined, signal),
    updateCurrentUser: (body) => client.request({ method: 'PATCH', path: '/me', body }),

    searchPlaces: (q, limit, signal) => get('/geo/search', { q, limit }, signal),
    reverseGeocode: (lat, lng, signal) => get('/geo/reverse', { lat, lng }, signal),

    listMarkets: (params, signal) => get('/markets', { ...params }, signal),
    getMarket: (marketId, at, signal) => get(`/markets/${enc(marketId)}`, { ...at }, signal),
    getMarketHome: (marketId, signal) => get(`/markets/${enc(marketId)}/home`, undefined, signal),
    listMarketCategories: (marketId, signal) =>
      get(`/markets/${enc(marketId)}/categories`, undefined, signal),
    listCategories: (signal) => get('/categories', undefined, signal),
    listMarketProducts: (marketId, params, signal) =>
      get(`/markets/${enc(marketId)}/products`, { ...params }, signal),
    marketSearchSuggestions: (marketId, q, signal) =>
      get(`/markets/${enc(marketId)}/search/suggestions`, { q, limit: 5 }, signal),
    listMarketPromotions: (marketId, signal) =>
      get(`/markets/${enc(marketId)}/promotions`, undefined, signal),
    getProduct: (productId, signal) => get(`/products/${enc(productId)}`, undefined, signal),
    searchProductsNearby: (params, signal) => get('/search/products', { ...params }, signal),

    listFavoriteMarkets: (at, signal) => get('/favorites/markets', { ...at }, signal),
    addFavoriteMarket: (marketId) =>
      client.request({ method: 'PUT', path: `/favorites/markets/${enc(marketId)}` }),
    removeFavoriteMarket: (marketId) =>
      client.request({ method: 'DELETE', path: `/favorites/markets/${enc(marketId)}` }),
    listMarketFavorites: (marketId, signal) =>
      get(`/markets/${enc(marketId)}/favorites`, undefined, signal),
    addFavoriteProduct: (productId) =>
      client.request({ method: 'PUT', path: `/favorites/products/${enc(productId)}` }),
    removeFavoriteProduct: (productId) =>
      client.request({ method: 'DELETE', path: `/favorites/products/${enc(productId)}` }),

    listCarts: (signal) => get('/carts', undefined, signal),
    getCart: (marketId, signal) => get(`/markets/${enc(marketId)}/cart`, undefined, signal),
    clearCart: (marketId) => client.request({ method: 'DELETE', path: `/markets/${enc(marketId)}/cart` }),
    setCartItemQuantity: (marketId, productId, quantity) =>
      client.request({
        method: 'PUT',
        path: `/markets/${enc(marketId)}/cart/items/${enc(productId)}`,
        body: { quantity },
      }),
    removeCartItem: (marketId, productId) =>
      client.request({ method: 'DELETE', path: `/markets/${enc(marketId)}/cart/items/${enc(productId)}` }),
    reserveFulfilment: (marketId, body) =>
      client.request({ method: 'PUT', path: `/markets/${enc(marketId)}/cart/fulfilment`, body }),
    listSlots: (marketId, type, addressId, signal) =>
      get(`/markets/${enc(marketId)}/slots`, { type, addressId }, signal),

    listAddresses: (signal) => get('/addresses', undefined, signal),
    createAddress: (body) => client.request({ method: 'POST', path: '/addresses', body }),
    updateAddress: (addressId, body) =>
      client.request({ method: 'PUT', path: `/addresses/${enc(addressId)}`, body }),
    deleteAddress: (addressId) => client.request({ method: 'DELETE', path: `/addresses/${enc(addressId)}` }),

    placeOrder: (marketId, idempotencyKey) =>
      client.request({
        method: 'POST',
        path: `/markets/${enc(marketId)}/orders`,
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    listOrders: (signal) => get('/orders', undefined, signal),
    getOrder: (orderId, signal) => get(`/orders/${enc(orderId)}`, undefined, signal),
    cancelOrder: (orderId) => client.request({ method: 'POST', path: `/orders/${enc(orderId)}/cancel` }),
    reorder: (orderId) => client.request({ method: 'POST', path: `/orders/${enc(orderId)}/reorder` }),

    listMyMarkets: (signal) => get('/merchant/markets', undefined, signal),
    createMyMarket: (body) => client.request({ method: 'POST', path: '/merchant/markets', body }),
    updateMyMarket: (marketId, body) =>
      client.request({ method: 'PUT', path: `/merchant/markets/${enc(marketId)}`, body }),
    searchSharedCatalog: (params, signal) => get('/merchant/catalog', { ...params }, signal),
    listMyListings: (marketId, params, signal) =>
      get(`/merchant/markets/${enc(marketId)}/listings`, { ...params }, signal),
    createListing: (marketId, body) =>
      client.request({ method: 'POST', path: `/merchant/markets/${enc(marketId)}/listings`, body }),
    updateListing: (marketId, productId, body) =>
      client.request({
        method: 'PUT',
        path: `/merchant/markets/${enc(marketId)}/listings/${enc(productId)}`,
        body,
      }),
    deleteListing: (marketId, productId) =>
      client.request({
        method: 'DELETE',
        path: `/merchant/markets/${enc(marketId)}/listings/${enc(productId)}`,
      }),
    listMyPromotions: (marketId, signal) =>
      get(`/merchant/markets/${enc(marketId)}/promotions`, undefined, signal),
    createPromotion: (marketId, body) =>
      client.request({ method: 'POST', path: `/merchant/markets/${enc(marketId)}/promotions`, body }),
    deletePromotion: (marketId, promotionId) =>
      client.request({
        method: 'DELETE',
        path: `/merchant/markets/${enc(marketId)}/promotions/${enc(promotionId)}`,
      }),
    listMarketOrders: (marketId, status, signal) =>
      get(`/merchant/markets/${enc(marketId)}/orders`, { status }, signal),
    advanceOrderStatus: (orderId, status) =>
      client.request({ method: 'POST', path: `/merchant/orders/${enc(orderId)}/status`, body: { status } }),
    uploadMedia: (marketId, file, purpose) =>
      client.request({
        method: 'POST',
        path: `/merchant/markets/${enc(marketId)}/media`,
        body: mediaForm(file, purpose),
        timeoutMs: UPLOAD_TIMEOUT_MS,
      }),
    createProductDraft: (marketId, body) =>
      client.request({
        method: 'POST',
        path: `/merchant/markets/${enc(marketId)}/product-drafts`,
        body,
        timeoutMs: DRAFT_TIMEOUT_MS,
      }),
    translateTexts: (body) => client.request({ method: 'POST', path: '/merchant/translations', body, timeoutMs: DRAFT_TIMEOUT_MS }),
    getShoppingListCapabilities: (signal) => get('/shopping-list', undefined, signal),
    readShoppingList: (marketId, request) =>
      client.request({
        method: 'POST',
        path: `/markets/${enc(marketId)}/shopping-list`,
        body: shoppingListForm(request),
        timeoutMs: DRAFT_TIMEOUT_MS,
      }),
  };
}
