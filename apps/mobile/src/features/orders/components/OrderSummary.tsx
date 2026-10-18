import { StyleSheet, Text, View } from 'react-native';

import type { Order } from '../../../shared/api';
import { formatDayLabel, formatTimeRange } from '../../../shared/format/dates';
import { colors, radii, spacing, typography } from '../../../shared/theme';
import { Price, SummaryRow } from '../../../shared/ui';
import { formatAddressLine } from '../../address/validation';

export function OrderSummary({ order }: { order: Order }) {
  return (
    <View style={styles.card}>
      <Text style={styles.heading} accessibilityRole="header">
        Delivery
      </Text>
      <Text style={styles.body}>
        {formatDayLabel(order.slot.startsAt)}, {formatTimeRange(order.slot.startsAt, order.slot.endsAt)}
      </Text>
      <Text style={styles.muted}>{formatAddressLine(order.address)}</Text>

      <Text style={[styles.heading, styles.spaced]} accessibilityRole="header">
        Items
      </Text>
      {order.items.map((item) => (
        <View key={item.productId} style={styles.item}>
          <Text style={styles.body}>
            {item.quantity} × {item.name}
          </Text>
          <Price money={item.lineTotal} accessibilityPrefix={`${item.quantity} times ${item.name}`} />
        </View>
      ))}

      <View style={styles.divider} />
      <SummaryRow label="Subtotal" money={order.subtotal} />
      {order.discountTotal.amountCents > 0 && (
        <SummaryRow label="Discount" money={order.discountTotal} negative />
      )}
      <SummaryRow label="Delivery fee" money={order.deliveryFee} />
      <SummaryRow label="Total" money={order.total} emphasized />
      <Text style={styles.muted}>Payment is simulated in this version. Nothing was charged.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: spacing.lg,
    borderRadius: radii.lg,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  heading: { ...typography.bodyStrong, color: colors.text, marginBottom: spacing.xs },
  spaced: { marginTop: spacing.lg },
  body: { ...typography.body, color: colors.text, flexShrink: 1 },
  muted: { ...typography.caption, color: colors.textMuted, marginTop: spacing.xs },
  item: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: spacing.md,
    paddingVertical: spacing.xs,
  },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: colors.border, marginVertical: spacing.md },
});
