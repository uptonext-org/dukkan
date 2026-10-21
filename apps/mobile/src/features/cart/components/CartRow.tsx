import { memo, useState } from 'react';
import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { CartItem } from '../../../shared/api';
import { formatMoneyForAccessibility } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, DiscountLabel, ImageTile, QuantityStepper, SuperscriptPrice } from '../../../shared/ui';

interface CartRowProps {
  item: CartItem;
  maxQuantity: number;
  busy: boolean;
  onChangeQuantity: (productId: string, quantity: number) => void;
}

/**
 * Cart row: quantity pill (start), 64 pt image, name, unit size, discount label; on the end side
 * the regular price struck through and the line total in `accent`, cents in superscript.
 * Tapping the pill reveals the − n + stepper.
 */
function CartRowComponent({ item, maxQuantity, busy, onChangeQuantity }: CartRowProps) {
  const styles = useStyles();
  const { t } = useTranslation();
  const [editing, setEditing] = useState(false);
  const discounted = item.lineDiscount.amountCents > 0;
  const unavailable = item.available === false;
  return (
    <View style={styles.row} testID={`cart-row-${item.productId}`}>
      <View style={styles.main}>
        <Pressable
          onPress={() => setEditing((e) => !e)}
          accessibilityRole="button"
          accessibilityLabel={t('cart.quantityPill', { count: item.quantity, name: item.name })}
          accessibilityHint={t('cart.quantityPillHint')}
          accessibilityState={{ expanded: editing }}
          style={styles.pill}>
          <AppText variant="bodyStrong" dense>
            {String(item.quantity)}
          </AppText>
        </Pressable>
        <ImageTile uri={item.imageUrl} size={64} radius={12} />
        <View style={styles.texts}>
          <AppText variant="body" dense numberOfLines={2}>
            {item.name}
          </AppText>
          <AppText variant="subhead" color="textSecondary" dense>
            {item.unitSize}
          </AppText>
          {discounted && item.promotion != null && <DiscountLabel label={item.promotion.label} />}
          {unavailable && (
            <AppText variant="caption" color="accent" dense>
              {t('product.unavailable')}
            </AppText>
          )}
        </View>
        <View style={styles.prices}>
          {discounted && <SuperscriptPrice money={item.lineSubtotal} color="textSecondary" strike small accessibilityPrefix={t('cart.regularPrice')} />}
          <SuperscriptPrice
            money={item.lineTotal}
            color={discounted ? 'accent' : 'text'}
            accessibilityPrefix={discounted ? t('cart.discountedPrice') : t('cart.linePrice')}
          />
        </View>
      </View>
      {editing && (
        <View style={styles.stepper}>
          <QuantityStepper
            value={item.quantity}
            max={maxQuantity}
            itemName={item.name}
            busy={busy}
            onChange={(next) => onChangeQuantity(item.productId, next)}
          />
          <AppText variant="caption" color="textSecondary" dense>
            {t('cart.unitPrice', { price: formatMoneyForAccessibility(item.unitPrice) })}
          </AppText>
        </View>
      )}
    </View>
  );
}

export const CartRow = memo(CartRowComponent);

const useStyles = makeStyles((t) => ({
  row: { paddingVertical: t.spacing.md, borderBottomWidth: 1, borderBottomColor: t.colors.separator, gap: t.spacing.sm },
  main: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  pill: {
    minWidth: 44,
    minHeight: 44,
    borderRadius: 22,
    backgroundColor: t.colors.surfaceMuted,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: t.spacing.sm,
  },
  texts: { flex: 1, gap: 2 },
  prices: { flexDirection: 'row', alignItems: 'flex-end', gap: t.spacing.sm },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, paddingStart: 56 },
}));
