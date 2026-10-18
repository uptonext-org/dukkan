/**
 * Original Dukkan palette. Text/background pairs meet WCAG AA (>= 4.5:1).
 */
export const colors = {
  primary: '#1F6F50',
  primaryPressed: '#17573F',
  primaryMuted: '#E3F0EA',
  onPrimary: '#FFFFFF',
  background: '#FAF7F2',
  surface: '#FFFFFF',
  surfaceAlt: '#F1ECE3',
  text: '#1C1B19',
  textMuted: '#5B5852',
  border: '#DDD6CB',
  promo: '#A43C12',
  onPromo: '#FFFFFF',
  danger: '#B3261E',
  dangerMuted: '#FBE9E7',
  warning: '#7A4A00',
  warningMuted: '#FFF2DC',
  disabled: '#B9B4AB',
  tileColors: ['#D6EADF', '#F3E1C7', '#E4DDF0', '#DCE7F2', '#F2DCDC', '#E6EDCF'],
} as const;

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const radii = { sm: 6, md: 10, lg: 16, pill: 999 } as const;

/** Minimum touch target (iOS HIG 44pt / Android 48dp - we use 48 where space allows). */
export const touchTarget = 44;

export const typography = {
  title: { fontSize: 24, fontWeight: '700' },
  heading: { fontSize: 18, fontWeight: '700' },
  body: { fontSize: 16, fontWeight: '400' },
  bodyStrong: { fontSize: 16, fontWeight: '600' },
  caption: { fontSize: 13, fontWeight: '400' },
} as const;

export const theme = { colors, spacing, radii, typography, touchTarget } as const;
