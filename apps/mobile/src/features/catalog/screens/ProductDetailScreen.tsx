import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketStackScreenProps } from '../../../navigation/types';
import { formatMoney, formatMoneyForAccessibility, forSpeech, isolate, needsOrganicPrefix } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, IconButton, ImageTile, Price, PromoPill, QuantityStepper, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { useCartQuantities } from '../../cart/api/hooks';
import { useMarketContext } from '../../market/MarketContext';
import { useToggleProductFavorite } from '../../favorites/api/hooks';
import { useProduct } from '../api/hooks';

function Section({ title, children }: { title: string; children: string }) {
  return (
    <View style={{ gap: 4 }}>
      <AppText variant="headline" accessibilityRole="header">
        {title}
      </AppText>
      <AppText variant="body" color="textSecondary">
        {children}
      </AppText>
    </View>
  );
}

/** Product detail inside a market: photo, promo, Bio prefix + tagline, prices, stepper, heart, details. */
export function ProductDetailScreen({ route, navigation }: MarketStackScreenProps<'MarketProduct'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const query = useProduct(route.params.productId);
  const { marketId } = useMarketContext();
  const cart = useCartQuantities(marketId);
  const { toggle } = useToggleProductFavorite();
  const back = () => navigation.goBack();

  const state = queryState(query);
  if (state !== null || query.data === undefined) {
    return <Screen header={<ScreenHeader title="" onBack={back} />}>{state}</Screen>;
  }
  const product = query.data;
  const quantity = cart.quantities.get(product.id) ?? 0;
  const discounted = product.promotionalPrice != null;
  const favorite = product.favorite === true;

  return (
    <Screen
      padded={false}
      header={
        <ScreenHeader
          title={product.name}
          onBack={back}
          trailing={
            <IconButton
              icon="heart"
              filled={favorite}
              selected={favorite}
              color={favorite ? colors.accent : colors.text}
              label={favorite ? t('favorites.remove', { name: product.name }) : t('favorites.add', { name: product.name })}
              onPress={() => toggle(product.id, favorite)}
              testID="product-favorite"
            />
          }
        />
      }
      footer={
        !product.available ? (
          <AppText variant="bodyStrong" color="accent" center>
            {t('product.unavailable')}
          </AppText>
        ) : quantity > 0 ? (
          <View style={styles.footerRow}>
            <QuantityStepper value={quantity} max={product.maxOrderQuantity} itemName={product.name} busy={cart.busyProductId === product.id} onChange={(next) => cart.setQuantity(product.id, next)} />
            <AppText variant="subhead" color="textSecondary" style={styles.flex}>
              {t('product.maxHint', { count: product.maxOrderQuantity })}
            </AppText>
          </View>
        ) : (
          <Button title={t('product.addToCart')} icon="plus" onPress={() => cart.setQuantity(product.id, 1)} loading={cart.busyProductId === product.id} testID="detail-add" />
        )
      }>
      <ScrollView contentContainerStyle={styles.content} testID="product-detail">
        <View>
          <ImageTile uri={product.imageUrl} aspectRatio={1.1} />
          {product.promotion != null && (
            <View style={styles.promo}>
              <PromoPill label={product.promotion.label} />
            </View>
          )}
        </View>
        <View style={styles.titleBlock}>
          {product.tagline != null && (
            <AppText variant="callout" color="taste">
              {product.tagline}
            </AppText>
          )}
          <AppText variant="title" accessibilityRole="header">
            {product.organic && needsOrganicPrefix(product.name, t('product.organic')) && <AppText variant="title" color="primary">{`${t('product.organic')} `}</AppText>}
            {product.name}
          </AppText>
          {product.brand != null && (
            <AppText variant="subhead" color="textSecondary">
              {product.brand}
            </AppText>
          )}
          <View style={styles.priceRow}>
            <Price money={product.promotionalPrice ?? product.price} variant="title" color={discounted ? 'accent' : 'text'} />
            {discounted && <Price money={product.price} variant="body" color="textSecondary" strike accessibilityPrefix={t('cart.regularPrice')} />}
          </View>
          <AppText variant="subhead" color="textSecondary" accessibilityLabel={forSpeech(product.comparisonPrice != null ? `${product.unitSize}, ${formatMoneyForAccessibility(product.comparisonPrice.price)} ${t('product.per')} ${t(`units.${product.comparisonPrice.unit}`)}` : product.unitSize)}>
            {product.unitSize}
            {product.comparisonPrice != null && ` · ${isolate(formatMoney(product.comparisonPrice.price))}/${t(`units.${product.comparisonPrice.unit}`)}`}
          </AppText>
        </View>
        {product.description != null && product.description !== '' && <Section title={t('product.description')}>{product.description}</Section>}
        {product.ingredients != null && product.ingredients !== '' && <Section title={t('product.ingredients')}>{product.ingredients}</Section>}
        <Section title={t('product.allergens')}>{product.allergens.length > 0 ? product.allergens.join(', ') : t('product.noAllergens')}</Section>
        {product.origin != null && product.origin !== '' && <Section title={t('product.origin')}>{product.origin}</Section>}
        <AppText variant="caption" color="textSecondary">
          {t('product.soldBy', { market: product.market.name })}
        </AppText>
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, gap: t.spacing.xl, paddingBottom: t.spacing.xxxl },
  promo: { position: 'absolute', bottom: t.spacing.md, start: t.spacing.md },
  titleBlock: { gap: t.spacing.xs },
  priceRow: { flexDirection: 'row', alignItems: 'baseline', gap: t.spacing.sm, marginTop: t.spacing.xs },
  footerRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  flex: { flex: 1 },
}));
