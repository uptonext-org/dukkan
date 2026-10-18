import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { ProductSummary } from '../api/types';
import { formatMoneyForAccessibility, unitLabel } from '../format/money';
import { colors, radii, spacing, touchTarget, typography } from '../theme';
import { Badge } from './Badge';
import { InitialsTile } from './InitialsTile';
import { Price } from './Price';
import { QuantityStepper } from './QuantityStepper';

/** Business default from the contract; the server enforces the real per-product limit. */
export const DEFAULT_MAX_ORDER_QUANTITY = 24;

export interface ProductTileProps {
  product: ProductSummary;
  quantity: number;
  onPress: (productId: string) => void;
  onChangeQuantity: (productId: string, quantity: number) => void;
  busy?: boolean;
  width?: number;
}

function ProductTileComponent({
  product,
  quantity,
  onPress,
  onChangeQuantity,
  busy = false,
  width,
}: ProductTileProps) {
  const { id, name, brand, unitSize, price, comparisonPrice, promotion, available } = product;
  const summary = [
    name,
    brand ?? undefined,
    unitSize,
    formatMoneyForAccessibility(price),
    promotion ? `Offer ${promotion.label}` : undefined,
    available ? undefined : 'Currently unavailable',
  ]
    .filter((part): part is string => part !== undefined)
    .join(', ');

  return (
    <View style={[styles.card, width !== undefined && { width }]}>
      <Pressable
        onPress={() => onPress(id)}
        accessibilityRole="button"
        accessibilityLabel={summary}
        accessibilityHint="Opens product details"
        style={({ pressed }) => [styles.body, pressed && styles.pressed]}>
        <View style={styles.imageRow}>
          <InitialsTile name={name} size={64} />
          {promotion && <Badge label={promotion.label} tone="promo" />}
        </View>
        <Text style={styles.name} numberOfLines={2}>
          {name}
        </Text>
        <Text style={styles.meta} numberOfLines={1}>
          {[brand, unitSize].filter(Boolean).join(' · ')}
        </Text>
        <Price money={price} />
        {comparisonPrice && (
          <Text style={styles.meta}>
            <Price money={comparisonPrice.price} size="small" /> / {unitLabel(comparisonPrice.unit)}
          </Text>
        )}
      </Pressable>
      <View style={styles.actions}>
        {!available ? (
          <Badge label="Unavailable" tone="neutral" />
        ) : quantity > 0 ? (
          <QuantityStepper
            value={quantity}
            max={DEFAULT_MAX_ORDER_QUANTITY}
            itemName={name}
            busy={busy}
            onChange={(next) => onChangeQuantity(id, next)}
          />
        ) : (
          <Pressable
            onPress={() => onChangeQuantity(id, 1)}
            disabled={busy}
            accessibilityRole="button"
            accessibilityLabel={`Add ${name} to cart`}
            accessibilityState={{ disabled: busy, busy }}
            style={({ pressed }) => [styles.add, pressed && styles.addPressed]}>
            <Text style={styles.addText}>Add</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}

export const ProductTile = memo(ProductTileComponent);

const styles = StyleSheet.create({
  card: {
    flex: 1,
    margin: spacing.xs,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  body: { padding: spacing.md, gap: spacing.xs },
  pressed: { backgroundColor: colors.primaryMuted },
  imageRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  name: { ...typography.bodyStrong, color: colors.text },
  meta: { ...typography.caption, color: colors.textMuted },
  actions: { paddingHorizontal: spacing.md, paddingBottom: spacing.md },
  add: {
    minHeight: touchTarget,
    borderRadius: radii.md,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addPressed: { backgroundColor: colors.primaryPressed },
  addText: { ...typography.bodyStrong, color: colors.onPrimary },
});
