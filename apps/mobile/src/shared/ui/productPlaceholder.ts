import type { TileTint } from '../theme';
import type { IconName } from './Icon';

export interface Placeholder {
  icon: IconName;
  tint: TileTint;
}

/**
 * Maps the server's `iconKey` (product vocabulary or category key, see ProductSummary.iconKey in
 * the contract) to an icon and a soft tile tint, so product lists stay recognisable until
 * merchants upload photos. Unknown or missing keys fall back to the generic package icon.
 */
const PLACEHOLDERS: Readonly<Record<string, Placeholder>> = {
  // product keys
  apple: { icon: 'apple', tint: 'sage' },
  banana: { icon: 'banana', tint: 'sand' },
  carrot: { icon: 'carrot', tint: 'sage' },
  cherry: { icon: 'cherry', tint: 'blush' },
  citrus: { icon: 'citrus', tint: 'sand' },
  'leafy-green': { icon: 'leafyGreen', tint: 'sage' },
  pepper: { icon: 'flame', tint: 'blush' },
  egg: { icon: 'egg', tint: 'sand' },
  milk: { icon: 'milk', tint: 'sky' },
  cheese: { icon: 'milk', tint: 'sand' },
  beef: { icon: 'beef', tint: 'blush' },
  sausage: { icon: 'ham', tint: 'blush' },
  poultry: { icon: 'drumstick', tint: 'blush' },
  fish: { icon: 'fish', tint: 'sky' },
  bread: { icon: 'sandwich', tint: 'sand' },
  pastry: { icon: 'croissant', tint: 'sand' },
  grain: { icon: 'wheat', tint: 'sand' },
  legume: { icon: 'bean', tint: 'sage' },
  pasta: { icon: 'soup', tint: 'sand' },
  coffee: { icon: 'coffee', tint: 'sand' },
  tea: { icon: 'leaf', tint: 'sage' },
  soda: { icon: 'soda', tint: 'sky' },
  beer: { icon: 'beer', tint: 'sand' },
  sweets: { icon: 'candy', tint: 'blush' },
  spice: { icon: 'flame', tint: 'sand' },
  oil: { icon: 'droplet', tint: 'sage' },
  sauce: { icon: 'droplet', tint: 'blush' },
  nut: { icon: 'nut', tint: 'sand' },
  // category keys (fallback for products without an own key)
  'fruit-veg': { icon: 'apple', tint: 'sage' },
  meat: { icon: 'beef', tint: 'blush' },
  dairy: { icon: 'milk', tint: 'sky' },
  grains: { icon: 'wheat', tint: 'sand' },
  spices: { icon: 'flame', tint: 'sand' },
  deli: { icon: 'salad', tint: 'sage' },
  frozen: { icon: 'snowflake', tint: 'sky' },
  drinks: { icon: 'soda', tint: 'sky' },
};

const FALLBACK: Placeholder = { icon: 'package', tint: 'sage' };

export function productPlaceholder(iconKey: string | null | undefined): Placeholder {
  return (iconKey != null ? PLACEHOLDERS[iconKey] : undefined) ?? FALLBACK;
}

/** Props for ImageTile: `<ImageTile uri={p.imageUrl} {...placeholderProps(p.iconKey)} />`. */
export function placeholderProps(iconKey: string | null | undefined): { tint: TileTint; placeholderIcon: IconName } {
  const { icon, tint } = productPlaceholder(iconKey);
  return { tint, placeholderIcon: icon };
}
