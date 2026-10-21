/**
 * Colour tokens from docs/design/design-system.md §1. Components read these from the theme
 * and never use literal colours. Light and dark share the same names.
 */
export interface ColorTokens {
  bg: string;
  surface: string;
  surfaceMuted: string;
  surfaceRose: string;
  tileSage: string;
  tileSky: string;
  tileBlush: string;
  tileSand: string;
  text: string;
  textSecondary: string;
  textPlaceholder: string;
  primary: string;
  primaryStrong: string;
  accent: string;
  /** Background behind `onAccent` text (the dark CTA uses #D2392F for 4.8:1, see §1). */
  accentFill: string;
  onAccent: string;
  promo: string;
  onPromo: string;
  taste: string;
  separator: string;
  overlayText: string;
  scrim: string;
  /** Shadow colour for the floating "+" button (y 1, blur 4, 12 %). */
  shadow: string;
}

export const lightColors: ColorTokens = {
  bg: '#FFFFFF',
  surface: '#FFFFFF',
  surfaceMuted: '#F3F1EC',
  surfaceRose: '#FCF1EE',
  tileSage: '#EEF0E3',
  tileSky: '#E6EEF3',
  tileBlush: '#F6E7E9',
  tileSand: '#F4EFE4',
  text: '#2B2B2B',
  textSecondary: '#5C5752',
  textPlaceholder: '#6E6862',
  primary: '#3F7A24',
  primaryStrong: '#2E5320',
  accent: '#C42F28',
  accentFill: '#C42F28',
  onAccent: '#FFFFFF',
  promo: '#F5DA4B',
  onPromo: '#2B2B2B',
  taste: '#7A6418',
  separator: '#ECE9E5',
  overlayText: '#FFFFFF',
  scrim: 'rgba(0,0,0,0.35)',
  shadow: '#000000',
};

export const darkColors: ColorTokens = {
  bg: '#121212',
  surface: '#1E1E1E',
  surfaceMuted: '#262523',
  surfaceRose: '#2A1F1D',
  tileSage: '#23261E',
  tileSky: '#1E2428',
  tileBlush: '#2A2022',
  tileSand: '#28251F',
  text: '#F2F0EC',
  textSecondary: '#B3ADA6',
  textPlaceholder: '#9C968F',
  primary: '#7CC25A',
  primaryStrong: '#3F7A24',
  accent: '#FF6B5E',
  accentFill: '#D2392F',
  onAccent: '#FFFFFF',
  promo: '#E8C93A',
  onPromo: '#2B2B2B',
  taste: '#D4B455',
  separator: '#2E2C2A',
  overlayText: '#FFFFFF',
  scrim: 'rgba(0,0,0,0.35)',
  shadow: '#000000',
};

export const radii = { label: 6, pill: 12, tile: 16, sheet: 24, full: 999 } as const;

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, xxxl: 32 } as const;

/** Screen gutter and the hero carousel peek (§1). */
export const layout = { gutter: 16, carouselPeek: 24 } as const;

/** HIG minimum touch target (accessibility.md). */
export const touchTarget = 44;

/** Cap for Dynamic Type on dense rows (§2). */
export const DENSE_MAX_FONT_SCALE = 1.6;

export type TileTint = 'sage' | 'sky' | 'blush' | 'sand' | 'rose';

export function tintColor(colors: ColorTokens, tint: TileTint | undefined): string {
  switch (tint) {
    case 'sky':
      return colors.tileSky;
    case 'blush':
      return colors.tileBlush;
    case 'sand':
      return colors.tileSand;
    case 'rose':
      return colors.surfaceRose;
    default:
      return colors.tileSage;
  }
}

const TINT_CYCLE: readonly TileTint[] = ['sage', 'sky', 'blush', 'sand'];

/** Stable alternating tint for placeholders (decision 24: uniform placeholders until real photos). */
export function tintForKey(key: string): TileTint {
  let hash = 0;
  for (let i = 0; i < key.length; i++) {
    hash = (hash * 31 + key.charCodeAt(i)) | 0;
  }
  return TINT_CYCLE[Math.abs(hash) % TINT_CYCLE.length] ?? 'sage';
}
