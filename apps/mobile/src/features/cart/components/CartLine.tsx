import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { CartItem } from '../../../shared/api';
import { colors, spacing, typography } from '../../../shared/theme';
import { Badge, DEFAULT_MAX_ORDER_QUANTITY, InitialsTile, Price, QuantityStepper } from '../../../shared/ui';

interface CartLineProps {
  item: CartItem;
  busy: boolean;
  onChangeQuantity: (productId: string, quantity: number) => void;
}

export const CartLine = memo(function CartLine({ item, busy, onChangeQuantity }: CartLineProps) {
  const discounted = item.lineDiscount.amountCents > 0;
  return (
    <View style={styles.row}>
      <InitialsTile name={item.name} size={56} />
      <View style={styles.info}>
        <Text style={styles.name}>{item.name}</Text>
        <Text style={styles.meta}>
          {item.unitSize} · <Price money={item.unitPrice} size="small" /> each
        </Text>
        {item.promotion && <Badge label={item.promotion.label} tone="promo" />}
        <View style={styles.bottom}>
          <QuantityStepper
            value={item.quantity}
            max={DEFAULT_MAX_ORDER_QUANTITY}
            itemName={item.name}
            busy={busy}
            onChange={(next) => onChangeQuantity(item.productId, next)}
          />
          <View style={styles.prices}>
            {discounted && (
              <Price money={item.lineSubtotal} size="small" strikethrough accessibilityPrefix="was" />
            )}
            <Price money={item.lineTotal} accessibilityPrefix={`Line total for ${item.name}`} />
          </View>
        </View>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    padding: spacing.lg,
    gap: spacing.md,
    backgroundColor: colors.surface,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  info: { flex: 1, gap: spacing.xs },
  name: { ...typography.bodyStrong, color: colors.text },
  meta: { ...typography.caption, color: colors.textMuted },
  bottom: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  prices: { alignItems: 'flex-end' },
});
