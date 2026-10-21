import { createContext, use, useMemo, type ReactNode } from 'react';
import { StyleSheet, useColorScheme } from 'react-native';

import { useLanguage } from '../i18n/LanguageProvider';
import { darkColors, layout, lightColors, radii, spacing, touchTarget, type ColorTokens } from './tokens';
import { createTypography, type Typography } from './typography';

export interface Theme {
  dark: boolean;
  colors: ColorTokens;
  type: Typography;
  spacing: typeof spacing;
  radii: typeof radii;
  layout: typeof layout;
  touchTarget: number;
}

const ThemeContext = createContext<Theme | null>(null);

/** Follows the system appearance (dark-mode.md: no in-app appearance switch) and the UI language's script. */
export function ThemeProvider({ children, scheme }: { children: ReactNode; scheme?: 'light' | 'dark' }) {
  const system = useColorScheme();
  const { language } = useLanguage();
  const dark = (scheme ?? system) === 'dark';
  const script = language === 'ar' ? 'arabic' : 'latin';
  const theme = useMemo<Theme>(
    () => ({
      dark,
      colors: dark ? darkColors : lightColors,
      type: createTypography(script),
      spacing,
      radii,
      layout,
      touchTarget,
    }),
    [dark, script],
  );
  return <ThemeContext value={theme}>{children}</ThemeContext>;
}

export function useTheme(): Theme {
  const theme = use(ThemeContext);
  if (theme === null) {
    throw new Error('useTheme must be used inside <ThemeProvider>.');
  }
  return theme;
}

/**
 * `const useStyles = makeStyles((t) => ({ ... }))` → a hook returning StyleSheet styles that are
 * rebuilt only when the theme changes (light/dark, Latin/Arabic font).
 */
type StyleObject = Parameters<typeof StyleSheet.create>[0];

export function makeStyles<T extends StyleObject>(factory: (theme: Theme) => T & StyleObject): () => Readonly<T> {
  const cache = new WeakMap<Theme, Readonly<T>>();
  return function useStyles(): Readonly<T> {
    const theme = useTheme();
    let styles = cache.get(theme);
    if (styles === undefined) {
      styles = StyleSheet.create(factory(theme));
      cache.set(theme, styles);
    }
    return styles;
  };
}
