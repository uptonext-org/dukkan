import { StyleSheet, Text, type StyleProp, type TextStyle } from 'react-native';

import type { Money } from '../api/types';
import { formatMoney, formatMoneyForAccessibility } from '../format/money';
import { colors, typography } from '../theme';

interface PriceProps {
  money: Money;
  size?: 'small' | 'regular' | 'large';
  /** Shown struck through, e.g. an original price next to a discount. */
  strikethrough?: boolean;
  /** Optional spoken prefix, e.g. "Total". */
  accessibilityPrefix?: string;
  style?: StyleProp<TextStyle>;
}

/** Displays a server-computed amount. The app never derives prices itself. */
export function Price({
  money,
  size = 'regular',
  strikethrough = false,
  accessibilityPrefix,
  style,
}: PriceProps) {
  const spoken = formatMoneyForAccessibility(money);
  return (
    <Text
      accessible
      accessibilityLabel={accessibilityPrefix !== undefined ? `${accessibilityPrefix} ${spoken}` : spoken}
      style={[styles[size], strikethrough && styles.strike, style]}>
      {formatMoney(money)}
    </Text>
  );
}

const styles = StyleSheet.create({
  small: { ...typography.caption, color: colors.textMuted },
  regular: { ...typography.bodyStrong, color: colors.text },
  large: { ...typography.heading, color: colors.text },
  strike: { textDecorationLine: 'line-through' },
});
