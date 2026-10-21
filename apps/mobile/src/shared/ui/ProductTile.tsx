import { memo } from 'react';
import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { ProductSummary } from '../api/types';
import { formatMoney, formatMoneyForAccessibility } from '../format/money';
import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';
import { IconButton } from './Button';
import { Icon } from './Icon';
import { ImageTile } from './ImageTile';
import { PromoPill } from './Pills';
import { Price } from './Price';
import { QuantityStepper } from './QuantityStepper';

export interface ProductTileProps {
  product: ProductSummary;
  quantity: number;
  onPress: (product: ProductSummary) => void;
  onChangeQuantity: (product: ProductSummary, quantity: number) => void;
  onToggleFavorite?: (product: ProductSummary) => void;
  busy?: boolean;
  /** Fixed width for carousel items; grid tiles stretch. */
  width?: number;
}

/**
 * Product tile per design-system.md §4.3: photo on `tileSage` (r16), promo pill bottom-start,
 * white "+" bottom-end that turns into − n +, gold tagline, "Bio " prefix, name ›, price
 * (accent + struck-through regular price when discounted), "550 g · 5,07 €/kg".
 */
function ProductTileComponent({ product, quantity, onPress, onChangeQuantity, onToggleFavorite, busy = false, width }: ProductTileProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { name, unitSize, price, promotionalPrice, comparisonPrice, promotion, available, organic, tagline } = product;
  const discounted = promotionalPrice != null;
  const effective = promotionalPrice ?? price;
  const unitLine = [
    unitSize,
    comparisonPrice != null ? `${formatMoney(comparisonPrice.price)}/${t(`units.${comparisonPrice.unit}`)}` : undefined,
  ]
    .filter(Boolean)
    .join(' · ');

  const spoken = [
    organic ? `${t('product.organic')} ${name}` : name,
    tagline ?? undefined,
    discounted
      ? t('product.spokenDiscounted', { price: formatMoneyForAccessibility(effective), was: formatMoneyForAccessibility(price) })
      : formatMoneyForAccessibility(price),
    unitLine,
    promotion?.label,
    available ? undefined : t('product.unavailable'),
    quantity > 0 ? t('product.inCart', { count: quantity }) : undefined,
  ]
    .filter((part): part is string => part !== undefined && part !== '')
    .join(', ');

  return (
    <View style={[styles.card, width !== undefined ? { width } : styles.grid]} testID={`product-tile-${product.id}`}>
      <View>
        <Pressable onPress={() => onPress(product)} accessibilityRole="button" accessibilityLabel={spoken} accessibilityHint={t('product.openHint')}>
          <ImageTile uri={product.imageUrl} tint="sage" />
        </Pressable>
        {promotion != null && (
          <View style={styles.promo} pointerEvents="none">
            <PromoPill label={promotion.label} />
          </View>
        )}
        {onToggleFavorite !== undefined && (
          <View style={styles.heart}>
            <IconButton
              icon="heart"
              filled={product.favorite === true}
              color={product.favorite === true ? colors.accent : colors.text}
              label={product.favorite === true ? t('favorites.remove', { name }) : t('favorites.add', { name })}
              selected={product.favorite === true}
              onPress={() => onToggleFavorite(product)}
              size={20}
            />
          </View>
        )}
        <View style={styles.action}>
          {!available ? null : quantity > 0 ? (
            <QuantityStepper
              appearance="floating"
              value={quantity}
              max={product.maxOrderQuantity}
              itemName={name}
              busy={busy}
              onChange={(next) => onChangeQuantity(product, next)}
            />
          ) : (
            <Pressable
              onPress={() => onChangeQuantity(product, 1)}
              disabled={busy}
              accessibilityRole="button"
              accessibilityLabel={t('product.add', { name })}
              accessibilityState={{ disabled: busy, busy }}
              style={({ pressed }) => [styles.plus, pressed && styles.pressed]}>
              <Icon name="plus" size={22} color={colors.text} />
            </Pressable>
          )}
        </View>
      </View>
      <Pressable
        onPress={() => onPress(product)}
        accessible={false}
        importantForAccessibility="no-hide-descendants"
        style={styles.texts}>
        {tagline != null && tagline !== '' && (
          <AppText variant="caption" color="taste" dense numberOfLines={1}>
            {tagline}
          </AppText>
        )}
        <View style={styles.nameRow}>
          <AppText variant="headline" dense numberOfLines={2} style={styles.name}>
            {organic && <AppText variant="headline" color="primary" dense>{`${t('product.organic')} `}</AppText>}
            {name}
          </AppText>
          <Icon name="chevron" size={18} color={colors.text} style={styles.chevron} />
        </View>
        <View style={styles.priceRow}>
          <Price money={effective} color={discounted ? 'accent' : 'text'} />
          {discounted && <Price money={price} variant="subhead" color="textSecondary" strike />}
        </View>
        <AppText variant="subhead" color="textSecondary" dense numberOfLines={1}>
          {unitLine}
        </AppText>
        {!available && (
          <AppText variant="caption" color="accent" dense>
            {t('product.unavailable')}
          </AppText>
        )}
      </Pressable>
    </View>
  );
}

export const ProductTile = memo(ProductTileComponent);

const useStyles = makeStyles((t) => ({
  card: { gap: t.spacing.sm },
  grid: { flex: 1 },
  promo: { position: 'absolute', bottom: t.spacing.sm, start: t.spacing.sm },
  heart: { position: 'absolute', top: 2, end: 2 },
  action: { position: 'absolute', bottom: t.spacing.sm, end: t.spacing.sm },
  plus: {
    width: 44,
    height: 44,
    borderRadius: t.radii.pill,
    backgroundColor: t.colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: t.colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 2,
  },
  pressed: { opacity: 0.7 },
  texts: { gap: 2, paddingHorizontal: 2 },
  nameRow: { flexDirection: 'row', alignItems: 'center' },
  name: { flexShrink: 1 },
  chevron: { marginStart: 2 },
  priceRow: { flexDirection: 'row', alignItems: 'baseline', gap: t.spacing.sm },
}));
