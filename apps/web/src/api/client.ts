import type { AuthResponse, CatalogReviewItem, CodeName, FeatureKey, FeatureSwitches, ImportResult, MarketInsights, MarketProfileRequest, Place, Promotion, PromotionRequest, TranslationResult, Listing, MarketStatus, MerchantMarket, MerchantOrderSettings, Order, OrderSettings, OrderStatus, PageResponse, Problem, ReviewStatus, User } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: Problem | undefined;

  constructor(status: number, code: string, problem?: Problem) {
    super(problem?.detail ?? code);
    this.status = status;
    this.code = code;
    this.problem = problem;
  }
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: User;
}

/** Where the session lives between page loads (sessionStorage in the browser). */
export interface SessionStore {
  load(): Session | null;
  save(session: Session | null): void;
}

export const browserSessionStore: SessionStore = {
  load() {
    try {
      const raw = sessionStorage.getItem('dukkan.session');
      return raw === null ? null : (JSON.parse(raw) as Session);
    } catch {
      return null;
    }
  },
  save(session) {
    try {
      if (session === null) {
        sessionStorage.removeItem('dukkan.session');
      } else {
        sessionStorage.setItem('dukkan.session', JSON.stringify(session));
      }
    } catch {
      // Storage blocked: the session lasts until the tab is reloaded.
    }
  },
};

type Fetch = typeof fetch;

interface RequestOptions {
  method?: string;
  body?: unknown;
  form?: FormData;
  anonymous?: boolean;
}

/**
 * Small API client: JSON in and out, Problem Details as {@link ApiError}, bearer token, and one
 * silent refresh (rotating refresh token, B-39) when an access token has expired.
 */
export function createApi(options: { baseUrl?: string; fetch?: Fetch; store?: SessionStore; language: () => string; onSessionChange?: (s: Session | null) => void }) {
  const baseUrl = options.baseUrl ?? '/api/v1';
  const doFetch: Fetch = options.fetch ?? ((input, init) => fetch(input, init));
  const store = options.store ?? browserSessionStore;
  let session = store.load();
  let refreshing: Promise<boolean> | null = null;

  const setSession = (next: Session | null) => {
    session = next;
    store.save(next);
    options.onSessionChange?.(next);
  };

  async function send<T>(path: string, request: RequestOptions = {}, retried = false): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': options.language() };
    if (request.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (request.anonymous !== true && session !== null) {
      headers.Authorization = `Bearer ${session.accessToken}`;
    }
    let response: Response;
    try {
      response = await doFetch(baseUrl + path, {
        method: request.method ?? 'GET',
        headers,
        body: request.form ?? (request.body !== undefined ? JSON.stringify(request.body) : null),
      });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR');
    }
    if (response.status === 401 && request.anonymous !== true && !retried && session !== null && (await refresh())) {
      return send<T>(path, request, true);
    }
    if (!response.ok) {
      let problem: Problem | undefined;
      try {
        problem = (await response.json()) as Problem;
      } catch {
        problem = undefined;
      }
      if (response.status === 401 && request.anonymous !== true) {
        setSession(null);
      }
      throw new ApiError(response.status, problem?.code ?? 'UNKNOWN', problem);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  function refresh(): Promise<boolean> {
    const current = session;
    if (current === null) {
      return Promise.resolve(false);
    }
    refreshing ??= send<AuthResponse>('/auth/refresh', { method: 'POST', body: { refreshToken: current.refreshToken }, anonymous: true })
      .then((auth) => {
        setSession({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user });
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
    return refreshing;
  }

  return {
    session: () => session,
    /** Signs in; an account that `allowed` rejects is signed out again and never becomes the session. */
    async login(email: string, password: string, allowed: (user: User) => boolean = () => true): Promise<User> {
      const auth = await send<AuthResponse>('/auth/login', { method: 'POST', body: { email, password }, anonymous: true });
      if (!allowed(auth.user)) {
        await send('/auth/logout', { method: 'POST', body: { refreshToken: auth.refreshToken }, anonymous: true }).catch(() => undefined);
        throw new ApiError(403, 'NO_PORTAL_ACCESS');
      }
      setSession({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user });
      return auth.user;
    },
    async logout(): Promise<void> {
      const current = session;
      setSession(null);
      if (current !== null) {
        await send('/auth/logout', { method: 'POST', body: { refreshToken: current.refreshToken }, anonymous: true }).catch(() => undefined);
      }
    },
    myMarkets: () => send<MerchantMarket[]>('/merchant/markets'),
    orders: (marketId: string, status?: OrderStatus) =>
      send<Order[]>(`/merchant/markets/${encodeURIComponent(marketId)}/orders${status === undefined ? '' : `?status=${status}`}`),
    advanceOrder: (orderId: string, status: OrderStatus) =>
      send<Order>(`/merchant/orders/${encodeURIComponent(orderId)}/status`, { method: 'POST', body: { status } }),
    /** Missing items while packing: units handed over per line (0 = missing). */
    adjustOrderItems: (orderId: string, items: { lineNo: number; fulfilledQuantity: number }[]) =>
      send<Order>(`/merchant/orders/${encodeURIComponent(orderId)}/items`, { method: 'PUT', body: { items } }),
    listings: (marketId: string, q: string, page: number) => {
      const query = new URLSearchParams({ page: String(page), size: '50' });
      if (q.trim() !== '') {
        query.set('q', q.trim());
      }
      return send<PageResponse<Listing>>(`/merchant/markets/${encodeURIComponent(marketId)}/listings?${query.toString()}`);
    },
    updateListing: (marketId: string, listingId: string, update: { price?: { amountCents: number; currency: 'EUR' }; available?: boolean }) =>
      send<Listing>(`/merchant/markets/${encodeURIComponent(marketId)}/listings/${encodeURIComponent(listingId)}`, { method: 'PUT', body: update }),
    importListings: (marketId: string, file: File, dryRun: boolean) => {
      const form = new FormData();
      form.append('file', file);
      return send<ImportResult>(`/merchant/markets/${encodeURIComponent(marketId)}/listings/import?dryRun=${String(dryRun)}`, { method: 'POST', form });
    },
    marketTypes: () => send<CodeName[]>('/market-types'),
    cuisines: () => send<CodeName[]>('/cuisines'),
    searchPlaces: (q: string) => send<Place[]>(`/geo/search?q=${encodeURIComponent(q)}&limit=5`),
    orderSettings: (marketId: string) => send<MerchantOrderSettings>(`/merchant/markets/${encodeURIComponent(marketId)}/order-settings`),
    updateOrderSettings: (marketId: string, settings: OrderSettings) =>
      send<MerchantOrderSettings>(`/merchant/markets/${encodeURIComponent(marketId)}/order-settings`, { method: 'PUT', body: settings }),
    updateMarket: (marketId: string, request: MarketProfileRequest) =>
      send<MerchantMarket>(`/merchant/markets/${encodeURIComponent(marketId)}`, { method: 'PUT', body: request }),
    uploadMedia: (marketId: string, file: File, purpose: 'LOGO' | 'COVER') => {
      const form = new FormData();
      form.append('file', file);
      form.append('purpose', purpose);
      return send<{ key: string; url: string }>(`/merchant/markets/${encodeURIComponent(marketId)}/media`, { method: 'POST', form });
    },
    insights: (marketId: string, days: number) => send<MarketInsights>(`/merchant/markets/${encodeURIComponent(marketId)}/insights?days=${days}`),
    promotions: (marketId: string) => send<Promotion[]>(`/merchant/markets/${encodeURIComponent(marketId)}/promotions`),
    createPromotion: (marketId: string, request: PromotionRequest) =>
      send<Promotion>(`/merchant/markets/${encodeURIComponent(marketId)}/promotions`, { method: 'POST', body: request }),
    endPromotion: (marketId: string, promotionId: string) =>
      send<undefined>(`/merchant/markets/${encodeURIComponent(marketId)}/promotions/${encodeURIComponent(promotionId)}`, { method: 'DELETE' }),
    translate: (kind: 'PROMOTION' | 'PRODUCT_NAME' | 'PRODUCT_DESCRIPTION' | 'MARKET_DESCRIPTION', values: Record<string, string>) =>
      send<TranslationResult>('/merchant/translations', { method: 'POST', body: { kind, fields: [{ key: 'text', values }] } }),
    adminMarkets: (status: MarketStatus | null) =>
      send<MerchantMarket[]>(`/admin/markets${status === null ? '' : `?status=${status}`}`),
    catalogReviewQueue: (status: ReviewStatus, page: number) =>
      send<PageResponse<CatalogReviewItem>>(`/admin/catalog/products?status=${status}&page=${String(page)}&size=20`),
    updateCatalogTexts: (productId: string, names: Record<string, string>, descriptions: Record<string, string>) =>
      send<CatalogReviewItem>(`/admin/catalog/products/${encodeURIComponent(productId)}/texts`, { method: 'PUT', body: { names, descriptions } }),
    reviewCatalogProduct: (productId: string, decision: 'PROMOTE' | 'KEEP_PRIVATE') =>
      send<CatalogReviewItem>(`/admin/catalog/products/${encodeURIComponent(productId)}/review`, { method: 'POST', body: { decision } }),
    features: () => send<FeatureSwitches>('/features'),
    adminFeatures: () => send<FeatureSwitches>('/admin/features'),
    adminSetFeature: (feature: FeatureKey, enabled: boolean) =>
      send<FeatureSwitches>(`/admin/features/${feature}`, { method: 'PUT', body: { enabled } }),
    adminSetMarketStatus: (marketId: string, status: MarketStatus) =>
      send<MerchantMarket>(`/admin/markets/${encodeURIComponent(marketId)}/status`, { method: 'POST', body: { status } }),
  };
}

export type Api = ReturnType<typeof createApi>;
