import { View, type StyleProp, type ViewStyle } from 'react-native';

import { forSpeech } from '../format/bidi';
import type { Money } from '../api/types';
import { formatMoney, formatMoneyForAccessibility, splitMoney } from '../format/money';
import type { ColorTokens, TextVariant } from '../theme';
import { AppText } from './AppText';

interface PriceProps {
  money: Money;
  variant?: TextVariant;
  color?: keyof ColorTokens;
  strike?: boolean;
  /** Spoken prefix, e.g. "Total". */
  accessibilityPrefix?: string;
  dense?: boolean;
  testID?: string;
}

/** A server-computed amount in de-DE format (`2,49 €`) in every language. */
export function Price({ money, variant = 'price', color = 'text', strike = false, accessibilityPrefix, dense = true, testID }: PriceProps) {
  const spoken = formatMoneyForAccessibility(money);
  return (
    <AppText
      testID={testID}
      variant={variant}
      color={color}
      strike={strike}
      dense={dense}
      accessible
      accessibilityLabel={forSpeech(accessibilityPrefix !== undefined ? `${accessibilityPrefix} ${spoken}` : spoken)}>
      {formatMoney(money)}
    </AppText>
  );
}

/**
 * Cart-row style price with superscript cents (`2¹⁴`), no currency sign (design-system.md §4.3).
 * Digits always run left-to-right, also inside an RTL row (right-to-left.md › Numbers).
 */
export function SuperscriptPrice({
  money,
  color = 'text',
  strike = false,
  small = false,
  style,
  accessibilityPrefix,
}: {
  money: Money;
  color?: keyof ColorTokens;
  strike?: boolean;
  small?: boolean;
  style?: StyleProp<ViewStyle>;
  accessibilityPrefix?: string;
}) {
  const { units, cents, negative } = splitMoney(money);
  const spoken = formatMoneyForAccessibility(money);
  return (
    <View
      style={[{ flexDirection: 'row', alignItems: 'flex-start', direction: 'ltr' }, style]}
      accessible
      accessibilityRole="text"
      accessibilityLabel={forSpeech(accessibilityPrefix !== undefined ? `${accessibilityPrefix} ${spoken}` : spoken)}>
      <AppText variant={small ? 'callout' : 'title'} color={color} strike={strike} dense>
        {negative ? '−' : ''}
        {units}
      </AppText>
      <AppText variant={small ? 'caption' : 'subhead'} color={color} strike={strike} dense style={{ marginTop: small ? 0 : 1 }}>
        {cents}
      </AppText>
    </View>
  );
}
