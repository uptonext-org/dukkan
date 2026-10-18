import { StyleSheet, Text, View } from 'react-native';

import type { Money } from '../api/types';
import { formatMoneyForAccessibility } from '../format/money';
import { colors, spacing, typography } from '../theme';
import { Price } from './Price';

interface SummaryRowProps {
  label: string;
  money: Money;
  emphasized?: boolean;
  negative?: boolean;
}

/** A label/amount row used in cart and order totals. */
export function SummaryRow({ label, money, emphasized = false, negative = false }: SummaryRowProps) {
  return (
    <View
      style={styles.row}
      accessible
      accessibilityLabel={`${label}: ${negative ? 'minus ' : ''}${formatMoneyForAccessibility(money)}`}>
      <Text style={emphasized ? styles.labelStrong : styles.label}>{label}</Text>
      <View style={styles.amount}>
        {negative && <Text style={styles.minus}>−</Text>}
        <Price
          money={money}
          size={emphasized ? 'large' : 'regular'}
          style={negative ? styles.discount : undefined}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.xs,
  },
  label: { ...typography.body, color: colors.textMuted },
  labelStrong: { ...typography.heading, color: colors.text },
  amount: { flexDirection: 'row', alignItems: 'center' },
  minus: { ...typography.bodyStrong, color: colors.promo },
  discount: { color: colors.promo },
});
