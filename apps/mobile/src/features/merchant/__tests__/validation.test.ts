import { marketDetail } from '../../../test/fixtures';
import { nextMerchantStatuses } from '../../orders/model';
import {
  emptyMarketProfile,
  emptyNewProduct,
  emptyPromotion,
  promotionEnd,
  promotionStart,
  endsNextDay,
  fromDraft,
  hoursOverlap,
  fromMerchantMarket,
  toMarketProfileRequest,
  toNewProductListing,
  toPromotionRequest,
  validateMarketProfile,
  validateNewProduct,
  validatePromotion,
  type DayHours,
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
      allergens: ['mustard'],
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
        allergens: ['mustard'],
      },
      price: { amountCents: 499, currency: 'EUR' },
      available: true,
      maxOrderQuantity: 10,
    });
  });
});

describe('capture draft → form', () => {
  it('fills what the draft knows, keeps what the merchant typed and merges allergens', () => {
    const typed = { ...emptyNewProduct(), name: { de: '', en: 'My name', ar: '' }, brand: 'Eigenmarke', allergens: ['milk'], price: '5,00' };
    const values = fromDraft(
      {
        engine: 'RULES',
        product: { name: { de: 'Sucuk Gazi' }, description: {}, keywords: [], unitSize: '400 g', netQuantity: 0.4, netUnit: 'kg', categoryId: null, cuisineCodes: [], organic: true, allergens: ['mustard', 'milk'] },
        price: { amountCents: 1249, currency: 'EUR' },
        catalogMatches: [],
        warnings: [],
      },
      typed,
    );
    expect(values).toMatchObject({
      name: { de: 'Sucuk Gazi', en: 'My name', ar: '' },
      brand: 'Eigenmarke',
      unitSize: '400 g',
      categoryId: null,
      organic: true,
      allergens: ['milk', 'mustard'],
      price: '12,49',
      net: { quantity: 0.4, unit: 'kg' },
      barcode: null,
      imageUrl: null,
    });
    expect(fromDraft({ engine: 'RULES', product: { name: {}, description: {}, keywords: [], cuisineCodes: [], organic: false, allergens: [] }, catalogMatches: [], warnings: [] }, typed).price).toBe('5,00');
  });
});

describe('market profile validation', () => {
  it('requires name, type, an address from search, a fulfilment option and valid hours', () => {
    const errors = validateMarketProfile({ ...emptyMarketProfile('de'), delivery: false, pickup: false, hours: emptyMarketProfile('de').hours.map((d) => ({ ...d, open: false })) });
    expect(errors).toMatchObject({ name: 'required', typeCode: 'required', addressLine: 'addressFromSearch', fulfilment: 'fulfilmentRequired', hours: 'hoursRequired', legalName: 'required' });
    const badHours = emptyMarketProfile('de').hours.map((d, i) => (i === 0 ? { ...d, intervals: [{ opensAt: '20:00', closesAt: '20:00' }] } : d));
    expect(validateMarketProfile({ ...emptyMarketProfile('de'), hours: badHours }).hours).toBe('hours');
  });

  it('allows overnight ranges and several ranges per day, but no overlaps', () => {
    const closed = { open: false, intervals: [{ opensAt: '08:00', closesAt: '20:00' }] };
    const week = (friday: DayHours, saturday: DayHours = closed, sunday: DayHours = closed, monday: DayHours = closed) => [monday, closed, closed, closed, friday, saturday, sunday];
    const lunch = { open: true, intervals: [{ opensAt: '08:00', closesAt: '12:00' }, { opensAt: '14:00', closesAt: '02:00' }] };
    expect(hoursOverlap(week(lunch))).toBe(false);
    expect(endsNextDay({ opensAt: '14:00', closesAt: '02:00' })).toBe(true);
    expect(endsNextDay({ opensAt: '20:00', closesAt: '00:00' })).toBe(true);
    expect(endsNextDay({ opensAt: '08:00', closesAt: '12:00' })).toBe(false);
    expect(hoursOverlap(week(lunch, { open: true, intervals: [{ opensAt: '01:00', closesAt: '09:00' }] }))).toBe(true);
    expect(hoursOverlap(week(closed, closed, { open: true, intervals: [{ opensAt: '22:00', closesAt: '09:00' }] }, { open: true, intervals: [{ opensAt: '08:00', closesAt: '12:00' }] }))).toBe(true);
    expect(hoursOverlap(week({ open: true, intervals: [{ opensAt: '08:00', closesAt: '13:00' }, { opensAt: '12:00', closesAt: '18:00' }] }))).toBe(true);
    const request = toMarketProfileRequest({ ...emptyMarketProfile('de'), hours: week(lunch) });
    expect(request.openingHours).toEqual([
      { dayOfWeek: 5, opensAt: '08:00', closesAt: '12:00' },
      { dayOfWeek: 5, opensAt: '14:00', closesAt: '02:00' },
    ]);
    expect(validateMarketProfile({ ...emptyMarketProfile('de'), hours: week({ open: true, intervals: [{ opensAt: '08:00', closesAt: '13:00' }, { opensAt: '12:00', closesAt: '18:00' }] }) }).hours).toBe('hoursOverlap');
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
  it('validates percent-off, quantity prices and offer prices', () => {
    expect(validatePromotion(emptyPromotion(), 'de')).toEqual({ title: 'namePrimaryRequired', productIds: 'productsRequired' });
    const multi = { ...emptyPromotion(), title: { de: '3 für 5', en: '', ar: '' }, type: 'MULTI_BUY' as const, tiers: [{ quantity: '1', price: '' }], productIds: ['p1'] };
    expect(validatePromotion(multi, 'de')).toEqual({ tiers: 'tiers' });
    const twice = { ...multi, tiers: [{ quantity: '3', price: '5' }, { quantity: '3', price: '4' }] };
    expect(validatePromotion(twice, 'de')).toEqual({ tiers: 'tiers' });
    const tiers = { ...multi, tiers: [{ quantity: '3', price: '5' }, { quantity: '7', price: '10' }] };
    expect(validatePromotion(tiers, 'de')).toEqual({});
    const request = toPromotionRequest(tiers, new Date('2026-10-20T10:00:00Z'));
    expect(request).toMatchObject({
      type: 'MULTI_BUY',
      percentOff: null,
      tiers: [
        { quantity: 3, price: { amountCents: 500 } },
        { quantity: 7, price: { amountCents: 1000 } },
      ],
      offers: null,
      weekdays: null,
      dailyFrom: null,
      validFrom: '2026-10-20T10:00:00.000Z',
      validUntil: '2026-10-27T10:00:00.000Z',
    });

    const fixed = { ...emptyPromotion(), title: { de: 'Angebot', en: '', ar: '' }, type: 'FIXED_PRICE' as const, offers: { p1: '5,00' }, productIds: ['p1'] };
    expect(validatePromotion(fixed, 'de', new Date(), { p1: 500 })).toEqual({ offers: 'offerBelowPrice' });
    const cheaper = { ...fixed, offers: { p1: '2' }, weekdays: [6, 1], allDay: false, dailyFrom: '07:00', dailyUntil: '10:00' };
    expect(validatePromotion(cheaper, 'de', new Date(), { p1: 500 })).toEqual({});
    expect(toPromotionRequest(cheaper)).toMatchObject({ offers: [{ productId: 'p1', offerPrice: { amountCents: 200 } }], weekdays: [1, 6], dailyFrom: '07:00', dailyUntil: '10:00' });
    expect(validatePromotion({ ...cheaper, weekdays: [] }, 'de')).toEqual({ weekdays: 'weekdaysRequired' });
    expect(validatePromotion({ ...cheaper, dailyUntil: '06:00' }, 'de')).toEqual({ daily: 'dailyWindow' });
  });

  it('starts later at midnight and ends the chosen number of days after the start', () => {
    const now = new Date(2026, 9, 20, 10, 30);
    const later = { ...emptyPromotion(), startInDays: 1, days: 3 };
    expect(promotionStart(later, now)).toEqual(new Date(2026, 9, 21, 0, 0));
    expect(promotionEnd(later, now)).toEqual(new Date(2026, 9, 24, 0, 0));
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

describe('fromMerchantMarket', () => {
  it('prefills the description in every language, so saving keeps all of them', () => {
    const market = { ...marketDetail(), status: 'ACTIVE' as const, description: 'Fresh simit', descriptions: { de: 'Frische Simit', en: 'Fresh simit', ar: 'سميت طازج' } };
    const values = fromMerchantMarket(market, 'en');
    expect(values.description).toEqual({ de: 'Frische Simit', en: 'Fresh simit', ar: 'سميت طازج' });
    expect(toMarketProfileRequest(values).description).toEqual({ de: 'Frische Simit', en: 'Fresh simit', ar: 'سميت طازج' });
  });

  it('keeps descriptions in languages the app does not edit', () => {
    const market = { ...marketDetail(), status: 'ACTIVE' as const, descriptions: { de: 'Frische Simit', tr: 'Taze simit' } };
    const values = fromMerchantMarket(market, 'de');
    values.description.de = 'Simit, frisch';
    expect(toMarketProfileRequest(values).description).toEqual({ de: 'Simit, frisch', tr: 'Taze simit' });
  });

  it('falls back to the requested language for older servers', () => {
    const market = { ...marketDetail(), status: 'ACTIVE' as const, description: 'Fresh simit' };
    expect(fromMerchantMarket(market, 'en').description.en).toBe('Fresh simit');
  });
});

describe('validateMarketProfile: seller information needed for approval', () => {
  const valid = () => ({
    ...emptyMarketProfile('de'),
    name: 'Anatolia',
    typeCode: 'SUPERMARKET',
    addressLine: 'Steindamm 30, 20099 Hamburg',
    location: { lat: 53.55, lng: 10.01 },
    phone: '+49 40 1234567',
    pickup: true,
    minimumOrderValue: '0',
    legalName: 'Anatolia GmbH',
  });

  it('asks for first and last name or a company with legal form', () => {
    expect(validateMarketProfile({ ...valid(), legalName: 'Alosh' }).legalName).toBe('legalNameWords');
    expect(validateMarketProfile({ ...valid(), legalName: 'Alosh Alrifai' }).legalName).toBeUndefined();
  });

  it('needs a postcode in the address and a phone number', () => {
    expect(validateMarketProfile({ ...valid(), addressLine: 'Hamburg (Hamm)' }).addressLine).toBe('addressPostcode');
    expect(validateMarketProfile({ ...valid(), phone: '' }).phone).toBe('required');
    expect(validateMarketProfile({ ...valid(), phone: '040 12' }).phone).toBe('phoneDigits');
    expect(validateMarketProfile({ ...valid(), addressLine: '20535 Hamburg (Hamm)', phone: '01575298254' })).not.toHaveProperty('phone');
  });
});
