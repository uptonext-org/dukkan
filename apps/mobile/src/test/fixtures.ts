import type {
  Banner,
  Cart,
  CartItem,
  CatalogProduct,
  MarketDetail,
  MarketHome,
  MarketSummary,
  Money,
  Order,
  ProductSummary,
  Slot,
} from '../shared/api';

export const eur = (amountCents: number): Money => ({ amountCents, currency: 'EUR' });

export const MARKET_ID = '00000000-0000-4000-8000-0000000000a1';

export function market(overrides: Partial<MarketSummary> = {}): MarketSummary {
  return {
    id: MARKET_ID,
    slug: 'anatolia-st-georg',
    name: 'Anatolia Markt',
    type: { code: 'GROCERY', name: 'Lebensmittelladen' },
    cuisines: [{ code: 'TURKISH', name: 'Türkisch' }],
    logoUrl: null,
    coverImageUrl: null,
    location: { lat: 53.5535, lng: 10.0123 },
    addressLine: 'Steindamm 30, 20099 Hamburg',
    distanceMeters: 1234,
    deliversToLocation: true,
    openNow: true,
    todayHours: '08:00–20:00',
    nextOpeningAt: null,
    fulfilment: {
      delivery: true,
      pickup: true,
      deliveryRadiusKm: 5,
      deliveryFee: eur(299),
      freeDeliveryFrom: eur(5000),
      minimumOrderValue: eur(1500),
    },
    activePromotionCount: 3,
    tags: ['HALAL'],
    ...overrides,
  };
}

export function marketDetail(overrides: Partial<MarketDetail> = {}): MarketDetail {
  return {
    ...market(),
    description: 'Türkische Spezialitäten',
    phone: null,
    openingHours: [{ dayOfWeek: 1, opensAt: '08:00', closesAt: '20:00' }],
    primaryLocale: 'de',
    imprint: { legalName: 'Anatolia GmbH', addressLine: 'Steindamm 30, 20099 Hamburg' },
    ...overrides,
  };
}

export function product(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    marketId: MARKET_ID,
    catalogProductId: '00000000-0000-4000-8000-0000000000c1',
    name: 'Äpfel Pink Lady',
    brand: null,
    tagline: 'süß-säuerlich',
    unitSize: '550 g',
    imageUrl: null,
    price: eur(349),
    promotionalPrice: eur(279),
    comparisonPrice: { price: eur(507), unit: 'kg' },
    promotion: { promotionId: '00000000-0000-4000-8000-0000000000p1', label: '20 % Rabatt', type: 'PERCENT_OFF' },
    available: true,
    organic: true,
    isNew: false,
    maxOrderQuantity: 3,
    favorite: false,
    ...overrides,
  };
}

export function cartItem(overrides: Partial<CartItem> = {}): CartItem {
  return {
    productId: '00000000-0000-4000-8000-000000000001',
    name: 'Salatgurke',
    unitSize: '1 Stück',
    imageUrl: null,
    unitPrice: eur(119),
    quantity: 2,
    lineSubtotal: eur(238),
    lineDiscount: eur(24),
    lineTotal: eur(214),
    promotion: { promotionId: '00000000-0000-4000-8000-0000000000p2', label: '10 % Rabatt', type: 'PERCENT_OFF' },
    available: true,
    maxOrderQuantity: 24,
    ...overrides,
  };
}

export function slot(id: string, startsAt: string, endsAt: string, overrides: Partial<Slot> = {}): Slot {
  return {
    id,
    type: 'DELIVERY',
    startsAt,
    endsAt,
    orderCutoffAt: '2026-10-21T06:00:00+02:00',
    fee: eur(0),
    available: true,
    remainingCapacity: 4,
    ...overrides,
  };
}

export function cart(overrides: Partial<Cart> = {}): Cart {
  return {
    id: '00000000-0000-4000-8000-0000000000d1',
    market: market(),
    items: [cartItem()],
    itemCount: 2,
    subtotal: eur(1156),
    discountTotal: eur(77),
    deliveryFee: eur(0),
    total: eur(1079),
    minimumOrderValue: eur(1500),
    meetsMinimumOrderValue: false,
    fulfilment: null,
    ...overrides,
  };
}

export function banner(overrides: Partial<Banner> = {}): Banner {
  return {
    id: '00000000-0000-4000-8000-0000000000b1',
    overline: 'Saftig und frisch',
    title: 'Obst und Gemüse der Saison',
    imageUrl: null,
    tint: 'sage',
    target: { type: 'PROMOTIONS', id: null },
    ...overrides,
  };
}

export function marketHome(overrides: Partial<MarketHome> = {}): MarketHome {
  return {
    market: market(),
    banners: [banner(), banner({ id: '00000000-0000-4000-8000-0000000000b2', title: 'Neu im Regal', overline: null, tint: 'sky', target: { type: 'NEW', id: null } })],
    sections: [
      { key: 'PROMOTIONS', title: 'Angebote der Woche', subtitle: null, categoryId: null, items: [product()] },
      {
        key: 'NEW',
        title: 'Neu im Markt',
        subtitle: 'Frisch eingetroffen',
        categoryId: null,
        items: [product({ id: '00000000-0000-4000-8000-000000000002', name: 'Sucuk', organic: false, tagline: null, promotion: null, promotionalPrice: null, isNew: true })],
      },
      { key: 'FREQUENTLY_ORDERED', title: 'Oft bestellt', subtitle: null, categoryId: null, items: [] },
    ],
    ...overrides,
  };
}

export function catalogProduct(overrides: Partial<CatalogProduct> = {}): CatalogProduct {
  return {
    id: '00000000-0000-4000-8000-0000000000e1',
    name: 'Ayran',
    brand: 'Sütaş',
    unitSize: '250 ml',
    imageUrl: null,
    categoryId: '00000000-0000-4000-8000-0000000000f1',
    cuisines: [{ code: 'TURKISH', name: 'Türkisch' }],
    organic: false,
    barcode: null,
    alreadyListed: false,
    ...overrides,
  };
}

export function order(overrides: Partial<Order> = {}): Order {
  return {
    id: '00000000-0000-4000-8000-0000000000o1',
    orderNumber: 'DK-20261020-000042',
    status: 'PLACED',
    placedAt: '2026-10-20T18:00:00+02:00',
    market: market(),
    fulfilmentType: 'DELIVERY',
    items: [{ lineNo: 1, productId: cartItem().productId, name: 'Salatgurke', unitPrice: eur(119), quantity: 2, fulfilledQuantity: null, lineTotal: eur(214) }],
    subtotal: eur(1700),
    discountTotal: eur(0),
    deliveryFee: eur(0),
    total: eur(1700),
    slot: slot('s1', '2026-10-21T08:00:00+02:00', '2026-10-21T09:00:00+02:00'),
    deliveryAddress: null,
    customerName: 'Ada L.',
    ...overrides,
  };
}

export const page = <T>(items: T[]) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: items.length > 0 ? 1 : 0 });
