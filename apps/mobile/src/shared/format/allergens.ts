import type { TFunction } from 'i18next';

/** The 14 allergens of EU Regulation 1169/2011, Annex II (codes as in the contract). */
export const ALLERGEN_CODES = [
  'gluten',
  'crustaceans',
  'eggs',
  'fish',
  'peanuts',
  'soy',
  'milk',
  'nuts',
  'celery',
  'mustard',
  'sesame',
  'sulphites',
  'lupin',
  'molluscs',
] as const;

export type AllergenCode = (typeof ALLERGEN_CODES)[number];

export function isAllergenCode(value: string): value is AllergenCode {
  return (ALLERGEN_CODES as readonly string[]).includes(value);
}

/** Localised allergen name; free-text values from older products are shown as they are. */
export function allergenLabel(t: TFunction, value: string): string {
  return isAllergenCode(value) ? t(`allergen.${value}`) : value;
}
