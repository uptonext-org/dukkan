import { StyleSheet, Text, View } from 'react-native';

import type { Cart } from '../../../shared/api';
import { formatDayLabel, formatTimeRange } from '../../../shared/format/dates';
import { formatMoney, formatMoneyForAccessibility } from '../../../shared/format/money';
import { colors, radii, spacing, typography } from '../../../shared/theme';
import { Button, SummaryRow } from '../../../shared/ui';
import { minimumOrderProgress } from '../model';

export function MinimumOrderNotice({ cart }: { cart: Cart }) {
  if (cart.meetsMinimumOrderValue) {
    return null;
  }
  const { ratio, remaining } = minimumOrderProgress(cart);
  const min = cart.minimumOrderValue;
  return (
    <View
      style={styles.notice}
      accessible
      accessibilityRole="text"
      accessibilityLiveRegion="polite"
      accessibilityLabel={`Minimum order value is ${formatMoneyForAccessibility(
        min,
      )}. Add ${formatMoneyForAccessibility(remaining)} more to check out.`}>
      <Text style={styles.noticeText}>
        Minimum order value is {formatMoney(min)}. Add {formatMoney(remaining)} more to check out.
      </Text>
      <View style={styles.track}>
        <View style={[styles.bar, { width: `${Math.round(ratio * 100)}%` }]} />
      </View>
    </View>
  );
}

export function DeliveryCard({ cart, onChoose }: { cart: Cart; onChoose: () => void }) {
  const delivery = cart.delivery;
  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle} accessibilityRole="header">
        Delivery
      </Text>
      {delivery ? (
        <Text style={styles.cardText}>
          {formatDayLabel(delivery.slot.startsAt)},{' '}
          {formatTimeRange(delivery.slot.startsAt, delivery.slot.endsAt)}
        </Text>
      ) : (
        <Text style={styles.cardText}>No delivery slot reserved yet.</Text>
      )}
      <Button
        title={delivery ? 'Change delivery slot' : 'Choose delivery slot'}
        variant="secondary"
        onPress={onChoose}
        style={styles.cardButton}
      />
    </View>
  );
}

export function CartTotals({ cart }: { cart: Cart }) {
  return (
    <View style={styles.card}>
      <SummaryRow label="Subtotal" money={cart.subtotal} />
      {cart.discountTotal.amountCents > 0 && (
        <SummaryRow label="Discount" money={cart.discountTotal} negative />
      )}
      <SummaryRow label="Delivery fee" money={cart.deliveryFee} />
      <View style={styles.divider} />
      <SummaryRow label="Total" money={cart.total} emphasized />
    </View>
  );
}

const styles = StyleSheet.create({
  notice: {
    margin: spacing.lg,
    marginBottom: 0,
    padding: spacing.md,
    borderRadius: radii.md,
    backgroundColor: colors.warningMuted,
  },
  noticeText: { ...typography.body, color: colors.warning },
  track: {
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.surface,
    marginTop: spacing.sm,
    overflow: 'hidden',
  },
  bar: { height: 6, backgroundColor: colors.warning },
  card: {
    margin: spacing.lg,
    marginBottom: 0,
    padding: spacing.lg,
    borderRadius: radii.lg,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  cardTitle: { ...typography.bodyStrong, color: colors.text },
  cardText: { ...typography.body, color: colors.textMuted, marginTop: spacing.xs },
  cardButton: { marginTop: spacing.md },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: colors.border, marginVertical: spacing.sm },
});
