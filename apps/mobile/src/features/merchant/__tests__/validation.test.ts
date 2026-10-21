import { nextMerchantStatuses } from '../../orders/model';
import {
  emptyMarketProfile,
  emptyNewProduct,
  emptyPromotion,
  toMarketProfileRequest,
  toNewProductListing,
  toPromotionRequest,
  validateMarketProfile,
  validateNewProduct,
  validatePromotion,
} from '../validation';

describe('create-product validation', () => {
  it('requires a name in the primary language, unit size, category, a valid price and quantity', () => {
    expect(validateNewProduct(emptyNewProduct(), 'de')).toEqual({
      name: 'namePrimaryRequired',
      unitSize: 'required',
      categoryId: 'categoryRequired',
      price: 'required',
    });
    const onlyArabicName = { ...emptyNewProduct(), name: { de: '', en: '', ar: 'سجق' } };
    expect(validateNewProduct(onlyArabicName, 'ar').name).toBeUndefined();
    expect(validateNewProduct(onlyArabicName, 'de').name).toBe('namePrimaryRequired');
  });

  it('checks price format, max quantity, keyword limits and lengths', () => {
    const base = { ...emptyNewProduct(), name: { de: 'Sucuk', en: '', ar: '' }, unitSize: '250 g', categoryId: 'c1' };
    expect(validateNewProduct({ ...base, price: '2,49' }, 'de')).toEqual({});
    expect(validateNewProduct({ ...base, price: '2,499' }, 'de').price).toBe('price');
    expect(validateNewProduct({ ...base, price: '0' }, 'de').price).toBe('price');
    expect(validateNewProduct({ ...base, price: '1', maxOrderQuantity: '100' }, 'de').maxOrderQuantity).toBe('maxQuantity');
    expect(validateNewProduct({ ...base, price: '1', keywords: Array.from({ length: 31 }, (_, i) => `k${i}`).join(',') }, 'de').keywords).toBe('keywords');
    expect(validateNewProduct({ ...base, price: '1', unitSize: 'x'.repeat(41) }, 'de').unitSize).toBe('tooLong');
  });

  it('builds a CreateListingRequest with localized names, keywords and cents', () => {
    const request = toNewProductListing({
      ...emptyNewProduct(),
      name: { de: 'Knoblauchwurst', en: 'Garlic sausage', ar: ' سجق ' },
      keywords: 'sucuk, Knoblauchwurst، سجق, sucuk',
      unitSize: '250 g',
      categoryId: 'c1',
      cuisineCodes: ['TURKISH'],
      allergens: 'Senf',
      price: '4,99',
      maxOrderQuantity: '10',
    });
    expect(request).toEqual({
      catalogProductId: null,
      newProduct: {
        name: { de: 'Knoblauchwurst', en: 'Garlic sausage', ar: 'سجق' },
        keywords: ['sucuk', 'Knoblauchwurst', 'سجق'],
        brand: null,
        unitSize: '250 g',
        categoryId: 'c1',
        cuisineCodes: ['TURKISH'],
        organic: false,
        allergens: ['Senf'],
      },
      price: { amountCents: 499, currency: 'EUR' },
      available: true,
      maxOrderQuantity: 10,
    });
  });
});

describe('market profile validation', () => {
  it('requires name, type, an address from search, a fulfilment option and valid hours', () => {
    const errors = validateMarketProfile({ ...emptyMarketProfile('de'), delivery: false, pickup: false, hours: emptyMarketProfile('de').hours.map((d) => ({ ...d, open: false })) });
    expect(errors).toMatchObject({ name: 'required', typeCode: 'required', addressLine: 'addressFromSearch', fulfilment: 'fulfilmentRequired', hours: 'hoursRequired', legalName: 'required' });
    const badHours = emptyMarketProfile('de').hours.map((d, i) => (i === 0 ? { ...d, opensAt: '20:00', closesAt: '08:00' } : d));
    expect(validateMarketProfile({ ...emptyMarketProfile('de'), hours: badHours }).hours).toBe('hours');
  });

  it('maps the form to MarketProfileRequest', () => {
    const request = toMarketProfileRequest({
      ...emptyMarketProfile('de'),
      name: 'Anatolia Markt',
      typeCode: 'GROCERY',
      addressLine: 'Steindamm 30, 20099 Hamburg',
      location: { lat: 53.55, lng: 10.01 },
      description: { de: 'Frisch', en: '', ar: 'طازج' },
      legalName: 'Anatolia GmbH',
      pickup: false,
    });
    expect(request).toMatchObject({
      name: 'Anatolia Markt',
      description: { de: 'Frisch', ar: 'طازج' },
      fulfilment: { delivery: true, pickup: false, deliveryRadiusKm: 5, deliveryFee: { amountCents: 299 }, freeDeliveryFrom: null, minimumOrderValue: { amountCents: 1500 } },
      legalName: 'Anatolia GmbH',
    });
    expect(request.openingHours).toHaveLength(6);
    expect(request.openingHours[0]).toEqual({ dayOfWeek: 1, opensAt: '08:00', closesAt: '20:00' });
  });
});

describe('promotions', () => {
  it('validates percent-off and multi-buy offers', () => {
    expect(validatePromotion(emptyPromotion(), 'de')).toEqual({ title: 'namePrimaryRequired', productIds: 'productsRequired' });
    const multi = { ...emptyPromotion(), title: { de: '3 für 2', en: '', ar: '' }, type: 'MULTI_BUY' as const, bundleQuantity: '1', productIds: ['p1'] };
    expect(validatePromotion(multi, 'de')).toEqual({ bundleQuantity: 'bundleQuantity', bundlePrice: 'required' });
    const request = toPromotionRequest({ ...multi, bundleQuantity: '3', bundlePrice: '5' }, new Date('2026-10-20T10:00:00Z'));
    expect(request).toMatchObject({ type: 'MULTI_BUY', percentOff: null, bundleQuantity: 3, bundlePrice: { amountCents: 500 }, validFrom: '2026-10-20T10:00:00.000Z', validUntil: '2026-10-27T10:00:00.000Z' });
  });

  it('rejects an end date that is not in the future before sending', () => {
    const valid = { ...emptyPromotion(), title: { de: 'Herbst', en: '', ar: '' }, productIds: ['p1'] };
    expect(validatePromotion(valid, 'de')).toEqual({});
    expect(validatePromotion({ ...valid, days: 0 }, 'de')).toEqual({ days: 'endDateFuture' });
    expect(validatePromotion({ ...valid, days: -1 }, 'de')).toEqual({ days: 'endDateFuture' });
    expect(validatePromotion({ ...valid, days: Number.NaN }, 'de')).toEqual({ days: 'endDateFuture' });
  });
});

describe('merchant order transitions (contract README)', () => {
  it('offers only the allowed next statuses', () => {
    expect(nextMerchantStatuses({ status: 'PLACED', fulfilmentType: 'DELIVERY' })).toEqual(['ACCEPTED', 'REJECTED']);
    expect(nextMerchantStatuses({ status: 'ACCEPTED', fulfilmentType: 'DELIVERY' })).toEqual(['OUT_FOR_DELIVERY']);
    expect(nextMerchantStatuses({ status: 'ACCEPTED', fulfilmentType: 'PICKUP' })).toEqual(['READY_FOR_PICKUP']);
    expect(nextMerchantStatuses({ status: 'READY_FOR_PICKUP', fulfilmentType: 'PICKUP' })).toEqual(['COMPLETED']);
    expect(nextMerchantStatuses({ status: 'OUT_FOR_DELIVERY', fulfilmentType: 'DELIVERY' })).toEqual(['COMPLETED']);
    for (const status of ['COMPLETED', 'REJECTED', 'CANCELLED'] as const) {
      expect(nextMerchantStatuses({ status, fulfilmentType: 'DELIVERY' })).toEqual([]);
    }
  });
});
