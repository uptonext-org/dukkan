import type { TextStyle } from 'react-native';

/**
 * Type scale from design-system.md §2. Font family names equal the PostScript names of the
 * bundled TTFs (assets/fonts), which is what iOS needs and what Android resolves from
 * assets/fonts/<name>.ttf. `fontWeight` is intentionally not set together with a custom family:
 * on Android it would make RN look for a synthetic variant; the weight lives in the family name.
 */
export type TextVariant =
  | 'largeTitle'
  | 'title'
  | 'bannerTitle'
  | 'headline'
  | 'body'
  | 'bodyStrong'
  | 'callout'
  | 'subhead'
  | 'caption'
  | 'tabLabel'
  | 'price';

type Weight = 400 | 500 | 600 | 700;

const SCALE: Record<TextVariant, { fontSize: number; lineHeight: number; weight: Weight }> = {
  largeTitle: { fontSize: 30, lineHeight: 36, weight: 700 },
  title: { fontSize: 24, lineHeight: 30, weight: 700 },
  bannerTitle: { fontSize: 28, lineHeight: 34, weight: 700 },
  headline: { fontSize: 18, lineHeight: 24, weight: 600 },
  body: { fontSize: 17, lineHeight: 24, weight: 400 },
  bodyStrong: { fontSize: 17, lineHeight: 24, weight: 600 },
  callout: { fontSize: 16, lineHeight: 22, weight: 400 },
  subhead: { fontSize: 15, lineHeight: 20, weight: 400 },
  caption: { fontSize: 13, lineHeight: 18, weight: 500 },
  tabLabel: { fontSize: 11, lineHeight: 13, weight: 500 },
  price: { fontSize: 18, lineHeight: 22, weight: 600 },
};

export type FontScript = 'latin' | 'arabic';

const FAMILIES: Record<FontScript, Record<Weight, string>> = {
  latin: { 400: 'Inter-Regular', 500: 'Inter-Medium', 600: 'Inter-SemiBold', 700: 'Inter-Bold' },
  arabic: {
    400: 'IBMPlexSansArabic-Regular',
    500: 'IBMPlexSansArabic-Medium',
    600: 'IBMPlexSansArabic-SemiBold',
    700: 'IBMPlexSansArabic-Bold',
  },
};

export function fontFamily(script: FontScript, weight: Weight): string {
  return FAMILIES[script][weight];
}

/** IBM Plex Sans Arabic's natural line height relative to its size (ascender + descender). */
const ARABIC_TIGHT_LINE_HEIGHT = 1.64;

export type Typography = Record<TextVariant, TextStyle>;

export function createTypography(script: FontScript): Typography {
  const result = {} as Typography;
  for (const [variant, spec] of Object.entries(SCALE) as [TextVariant, (typeof SCALE)[TextVariant]][]) {
    // Arabic glyphs sit taller; give them a little more line height (right-to-left.md suggests
    // balancing Arabic next to Latin by size; we keep sizes equal and add leading instead).
    // The tab label is the tightest style (11/13); Plex Arabic needs ~1.6× its size there or the
    // descenders are clipped (Arabic tab bar labels), so it gets the font's own line height.
    const lineHeight =
      script === 'arabic' ? (variant === 'tabLabel' ? Math.round(spec.fontSize * ARABIC_TIGHT_LINE_HEIGHT) : Math.round(spec.lineHeight * 1.12)) : spec.lineHeight;
    result[variant] = { fontSize: spec.fontSize, lineHeight, fontFamily: FAMILIES[script][spec.weight] };
  }
  return result;
}
