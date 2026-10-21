import { placeholderProps, productPlaceholder } from '../productPlaceholder';

describe('productPlaceholder', () => {
  it('maps product keys from the contract vocabulary to icons and tints', () => {
    expect(productPlaceholder('apple')).toEqual({ icon: 'apple', tint: 'sage' });
    expect(productPlaceholder('poultry').icon).toBe('drumstick');
    expect(productPlaceholder('soda').icon).toBe('soda');
  });

  it('maps category keys used as fallback', () => {
    expect(productPlaceholder('dairy').icon).toBe('milk');
    expect(productPlaceholder('frozen').icon).toBe('snowflake');
  });

  it('falls back to the generic package icon for unknown or missing keys', () => {
    expect(productPlaceholder('spaceship').icon).toBe('package');
    expect(productPlaceholder(null).icon).toBe('package');
    expect(placeholderProps(undefined)).toEqual({ tint: 'sage', placeholderIcon: 'package' });
  });
});
