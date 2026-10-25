import { FlatList, Pressable, RefreshControl, SectionList, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { GeoPoint, MarketSummary, ProductSummary, RecentMarket } from '../../../shared/api';
import { formatShortDate, forSpeech } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, CardSkeletons, EmptyState, ErrorState, Icon, ImageTile, placeholderProps } from '../../../shared/ui';
import { useAuthGate } from '../../auth/AuthGate';
import { useAuth } from '../../auth/AuthProvider';
import { useFavoriteMarkets, useToggleMarketFavorite } from '../../favorites/api/hooks';
import { useRecentMarkets } from '../api/hooks';
import { MarketCard } from './MarketCard';

type Row = { kind: 'favorite'; market: MarketSummary } | { kind: 'bought'; item: RecentMarket };

/**
 * Start page view "Meine Märkte" next to list and map, in two parts that are told apart by their
 * heading and a label on every market: the markets the customer marked as favourites (each with a
 * button to take it off the list) and the markets they bought from, with what they bought there (one
 * tap back to a product) and their last order. A market can be in both. Guests are asked to sign in.
 */
export function MyMarkets({
  center,
  header,
  onOpenMarket,
  onOpenProduct,
  onOpenOrder,
  onDiscover,
}: {
  center: GeoPoint;
  header: React.ReactElement;
  onOpenMarket: (market: MarketSummary) => void;
  onOpenProduct: (marketId: string, product: ProductSummary) => void;
  onOpenOrder: (orderId: string) => void;
  onDiscover: () => void;
}) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { status } = useAuth();
  const { requireAuth } = useAuthGate();
  const signedIn = status === 'signedIn';
  const favorites = useFavoriteMarkets();
  const { toggle } = useToggleMarketFavorite();
  // Only markets with orders: visits on this device are not part of this view.
  const bought = useRecentMarkets([], { lat: center.lat, lng: center.lng }, signedIn, signedIn);

  if (!signedIn) {
    return (
      <View style={styles.flex}>
        {header}
        <EmptyState icon="store" title={t('myMarkets.emptyTitle')} message={t('myMarkets.signInMessage')} actionLabel={t('auth.signIn')} onAction={() => requireAuth('favorites')} />
      </View>
    );
  }
  if ((favorites.isPending || bought.isPending) && favorites.fetchStatus !== 'idle') {
    return (
      <View style={styles.flex}>
        {header}
        <CardSkeletons />
      </View>
    );
  }
  const boughtItems = (bought.data ?? []).filter((m) => m.orderCount > 0);
  const favoriteItems = favorites.data ?? [];
  if ((favorites.isError || bought.isError) && boughtItems.length === 0 && favoriteItems.length === 0) {
    return (
      <View style={styles.flex}>
        {header}
        <ErrorState error={favorites.error ?? bought.error} onRetry={() => void Promise.all([favorites.refetch(), bought.refetch()])} />
      </View>
    );
  }
  const sections: { key: 'favorites' | 'bought'; title: string; data: Row[] }[] = [
    { key: 'favorites' as const, title: t('myMarkets.favorites'), data: favoriteItems.map((market): Row => ({ kind: 'favorite', market })) },
    { key: 'bought' as const, title: t('myMarkets.bought'), data: boughtItems.map((item): Row => ({ kind: 'bought', item })) },
  ].filter((section) => section.data.length > 0);

  return (
    <SectionList
      sections={sections}
      keyExtractor={(row) => `${row.kind}-${row.kind === 'favorite' ? row.market.id : row.item.market.id}`}
      ListHeaderComponent={header}
      ListEmptyComponent={
        <EmptyState icon="store" title={t('myMarkets.emptyTitle')} message={t('myMarkets.emptyMessage')} actionLabel={t('myMarkets.discover')} onAction={onDiscover} />
      }
      contentContainerStyle={styles.list}
      ItemSeparatorComponent={Separator}
      stickySectionHeadersEnabled={false}
      refreshControl={
        <RefreshControl
          refreshing={favorites.isRefetching || bought.isRefetching}
          onRefresh={() => void Promise.all([favorites.refetch(), bought.refetch()])}
          tintColor={colors.primary}
        />
      }
      testID="my-markets"
      renderSectionHeader={({ section }) => (
        <View style={styles.sectionHeader} testID={`my-markets-${section.key}`}>
          <Icon name={section.key === 'favorites' ? 'heart' : 'basket'} size={20} color={section.key === 'favorites' ? colors.accent : colors.primary} filled={section.key === 'favorites'} />
          <AppText variant="title" accessibilityRole="header">
            {section.title}
          </AppText>
        </View>
      )}
      renderItem={({ item: row }) =>
        row.kind === 'favorite' ? (
          <View style={styles.item} testID={`my-favorite-${row.market.id}`}>
            <MarketCard market={row.market} onPress={onOpenMarket} compact />
            <View style={styles.row}>
              <Pill icon="heart" text={t('myMarkets.favoriteLabel')} />
              <Button
                title={t('myMarkets.removeFavorite')}
                variant="ghost"
                icon="trash"
                onPress={() => toggle(row.market.id, true)}
                accessibilityLabel={t('myMarkets.removeFavoriteFor', { name: row.market.name })}
                testID={`my-favorite-remove-${row.market.id}`}
              />
            </View>
          </View>
        ) : (
          <View style={styles.item} testID={`my-market-${row.item.market.id}`}>
            <MarketCard market={row.item.market} onPress={onOpenMarket} compact />
            <View style={styles.row}>
              <Pill icon="basket" text={t('myMarkets.boughtLabel')} />
              <AppText variant="subhead" color="textSecondary" style={styles.flex}>
                {row.item.lastOrderAt !== null ? t('myMarkets.lastOrder', { date: formatShortDate(row.item.lastOrderAt), count: row.item.orderCount }) : ''}
              </AppText>
            </View>
            {row.item.recentProducts.length > 0 && (
              <View style={styles.bought}>
                <AppText variant="headline" dense>
                  {t('myMarkets.boughtHere')}
                </AppText>
                <FlatList
                  horizontal
                  data={row.item.recentProducts}
                  keyExtractor={(p) => p.id}
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={styles.products}
                  accessibilityLabel={t('myMarkets.boughtHere')}
                  renderItem={({ item: product }) => (
                    <Pressable
                      onPress={() => onOpenProduct(row.item.market.id, product)}
                      accessibilityRole="button"
                      accessibilityLabel={forSpeech(product.name)}
                      style={({ pressed }) => [styles.product, pressed && styles.pressed]}
                      testID={`my-market-product-${product.id}`}
                    >
                      <ImageTile uri={product.imageUrl} size={72} radius={14} {...placeholderProps(product.iconKey)} />
                      <AppText variant="caption" dense numberOfLines={2} style={styles.productName}>
                        {product.name}
                      </AppText>
                    </Pressable>
                  )}
                />
              </View>
            )}
            {row.item.lastOrderId !== null && (
              <Button
                title={t('myMarkets.lastOrderButton')}
                variant="secondary"
                icon="receipt"
                onPress={() => onOpenOrder(row.item.lastOrderId as string)}
                testID={`my-market-order-${row.item.market.id}`}
              />
            )}
          </View>
        )
      }
    />
  );
}

/** "Favorit" or "Gekauft" next to a market, so the two parts stay apart when a market is in both. */
function Pill({ icon, text }: { icon: 'heart' | 'basket'; text: string }) {
  const styles = useStyles();
  const { colors } = useTheme();
  return (
    <View style={styles.pill}>
      <Icon name={icon} size={14} color={icon === 'heart' ? colors.accent : colors.primary} filled={icon === 'heart'} />
      <AppText variant="caption" dense>
        {text}
      </AppText>
    </View>
  );
}

function Separator() {
  return <View style={{ height: 28 }} />;
}

const useStyles = makeStyles((t) => ({
  flex: { flex: 1 },
  list: { paddingBottom: t.spacing.xxxl },
  item: { paddingHorizontal: t.layout.gutter, gap: t.spacing.sm },
  bought: { gap: t.spacing.xs },
  products: { gap: t.spacing.md },
  product: { width: 80, gap: 4 },
  productName: { width: 80 },
  pressed: { opacity: 0.6 },
  row: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm, flexWrap: 'wrap' },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm, paddingHorizontal: t.layout.gutter, paddingTop: t.spacing.lg, paddingBottom: t.spacing.sm },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: t.spacing.sm,
    paddingVertical: 2,
    borderRadius: 999,
    backgroundColor: t.colors.surfaceRose,
  },
}));
