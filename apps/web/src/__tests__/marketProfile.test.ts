import { describe, expect, it } from 'vitest';

import type { MerchantMarket } from '../api/types';
import { formField, fromMarket, hoursOverlap, parseAmount, toRequest, type ProfileForm } from '../pages/marketProfile';

const merchantMarket: MerchantMarket = {
  id: 'm1',
  slug: 'anatolia',
  name: 'Anatolia Frischmarkt',
  status: 'ACTIVE',
  primaryLocale: 'de',
  addressLine: 'Steindamm 30, 20099 Hamburg',
  type: { code: 'GROCERY', name: 'Lebensmittel' },
  imprint: { legalName: 'Anatolia GmbH', addressLine: 'Steindamm 30', registerInfo: null },
  cuisines: [{ code: 'turkish', name: 'Türkisch' }],
  description: 'Frisch',
  descriptions: { de: 'Frisch', ar: 'طازج' },
  location: { lat: 53.55, lng: 10.01 },
  tags: ['HALAL'],
  fulfilment: {
    delivery: true,
    pickup: true,
    deliveryRadiusKm: 2.5,
    deliveryFee: { amountCents: 290, currency: 'EUR' },
    freeDeliveryFrom: null,
    minimumOrderValue: { amountCents: 1500, currency: 'EUR' },
  },
  openingHours: [
    { dayOfWeek: 1, opensAt: '08:00', closesAt: '13:00' },
    { dayOfWeek: 1, opensAt: '14:00', closesAt: '20:00' },
    { dayOfWeek: 6, opensAt: '18:00', closesAt: '02:00' },
  ],
};

function form(overrides: Partial<ProfileForm> = {}): ProfileForm {
  return { ...fromMarket(merchantMarket, 'de'), ...overrides };
}

describe('market profile form', () => {
  it('round-trips a market into the same request', () => {
    const f = fromMarket(merchantMarket, 'de');
    expect(f.radiusKm).toBe('2,5');
    expect(f.fee).toBe('2,90');
    expect(f.minimum).toBe('15,00');
    expect(f.hours[0]).toHaveLength(2);
    expect(f.hours[5]).toEqual([{ opensAt: '18:00', closesAt: '02:00' }]);
    const result = toRequest(f);
    expect('request' in result && result.request).toEqual({
      name: 'Anatolia Frischmarkt',
      typeCode: 'GROCERY',
      cuisineCodes: ['turkish'],
      description: { de: 'Frisch', ar: 'طازج' },
      primaryLocale: 'de',
      addressLine: 'Steindamm 30, 20099 Hamburg',
      location: { lat: 53.55, lng: 10.01 },
      phone: null,
      logoUrl: null,
      coverImageUrl: null,
      tags: ['HALAL'],
      fulfilment: {
        delivery: true,
        pickup: true,
        deliveryRadiusKm: 2.5,
        deliveryFee: { amountCents: 290, currency: 'EUR' },
        freeDeliveryFrom: null,
        minimumOrderValue: { amountCents: 1500, currency: 'EUR' },
      },
      openingHours: merchantMarket.openingHours,
      legalName: 'Anatolia GmbH',
      registerInfo: null,
    });
  });

  it('keeps every description language, falling back to the requested one for older servers', () => {
    expect(fromMarket(merchantMarket, 'en').descriptions).toEqual({ de: 'Frisch', ar: 'طازج' });
    const older: MerchantMarket = { ...merchantMarket };
    delete older.descriptions;
    expect(fromMarket(older, 'en').descriptions).toEqual({ en: 'Frisch' });
  });

  it('names every problem instead of sending', () => {
    const result = toRequest(form({ name: 'A', delivery: false, pickup: false, minimum: 'abc', location: null, hours: [[], [], [], [], [], [], []] }));
    expect('problems' in result && result.problems).toEqual({
      name: 'profile.problem.name',
      delivery: 'profile.problem.fulfilment',
      minimum: 'profile.problem.amount',
      addressLine: 'profile.problem.address',
      hours: 'profile.problem.hoursRequired',
    });
  });

  it('drops delivery details when only pickup is offered', () => {
    const result = toRequest(form({ delivery: false, radiusKm: '', fee: '' }));
    expect('request' in result && result.request.fulfilment).toEqual({
      delivery: false,
      pickup: true,
      deliveryRadiusKm: null,
      deliveryFee: null,
      freeDeliveryFrom: null,
      minimumOrderValue: { amountCents: 1500, currency: 'EUR' },
    });
  });

  it('checks the delivery radius and amounts', () => {
    expect(toRequest(form({ radiusKm: '60' }))).toEqual({ problems: { radiusKm: 'profile.problem.radius' } });
    expect(toRequest(form({ fee: '2,999' }))).toEqual({ problems: { fee: 'profile.problem.amount' } });
    expect(parseAmount('0')).toBe(0);
    expect(parseAmount('2.5 €')).toBe(250);
    expect(parseAmount('-1')).toBeNull();
  });

  it('finds overlapping and equal opening times like the server', () => {
    const empty = (): ProfileForm['hours'] => [[], [], [], [], [], [], []];
    const overlapping = empty();
    overlapping[0] = [
      { opensAt: '08:00', closesAt: '13:00' },
      { opensAt: '12:00', closesAt: '18:00' },
    ];
    expect(hoursOverlap(overlapping)).toBe(true);
    // Sunday night into Monday morning.
    const wrapping = empty();
    wrapping[6] = [{ opensAt: '20:00', closesAt: '03:00' }];
    wrapping[0] = [{ opensAt: '02:00', closesAt: '10:00' }];
    expect(hoursOverlap(wrapping)).toBe(true);
    wrapping[0] = [{ opensAt: '03:00', closesAt: '10:00' }];
    expect(hoursOverlap(wrapping)).toBe(false);
    const same = empty();
    same[2] = [{ opensAt: '09:00', closesAt: '09:00' }];
    expect(toRequest(form({ hours: same }))).toEqual({ problems: { hours: 'profile.problem.sameTime' } });
  });

  it('maps server field errors to the form fields', () => {
    expect(formField('openingHours[2]')).toBe('hours');
    expect(formField('fulfilment.deliveryFee.amountCents')).toBe('fee');
    expect(formField('fulfilment')).toBe('delivery');
    expect(formField('description')).toBe('descriptions');
    expect(formField('legalName')).toBe('legalName');
    expect(formField('somethingElse')).toBeNull();
  });
});
