import { Text, type TextProps } from 'react-native';

import { DENSE_MAX_FONT_SCALE, useTheme, type ColorTokens, type TextVariant } from '../theme';

export interface AppTextProps extends TextProps {
  variant?: TextVariant;
  color?: keyof ColorTokens;
  /** Caps Dynamic Type at 1.6× for dense rows (design-system.md §2). */
  dense?: boolean;
  strike?: boolean;
  center?: boolean;
}

/**
 * Text with the theme's type scale and colour tokens. Alignment is left to the natural
 * direction (Fabric resolves it from the root `direction`), so it mirrors in Arabic.
 */
export function AppText({
  variant = 'body',
  color = 'text',
  dense = false,
  strike = false,
  center = false,
  style,
  ...props
}: AppTextProps) {
  const theme = useTheme();
  return (
    <Text
      maxFontSizeMultiplier={dense ? DENSE_MAX_FONT_SCALE : undefined}
      {...props}
      style={[
        theme.type[variant],
        { color: theme.colors[color] },
        strike && { textDecorationLine: 'line-through' },
        center && { textAlign: 'center' },
        style,
      ]}
    />
  );
}
