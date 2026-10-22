import { FlatList, Pressable, RefreshControl, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { GeoPoint, MarketSummary, ProductSummary, RecentMarket } from '../../../shared/api';
import { formatShortDate, forSpeech } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, CardSkeletons, EmptyState, ErrorState, ImageTile, placeholderProps } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';
import { useRecentMarkets } from '../api/hooks';
import { useVisitedMarkets, type MarketVisit } from '../visits/VisitedMarkets';
import { MarketCard } from './MarketCard';

export interface MyMarket extends RecentMarket {
  /** Last visit on this device, if any. */
  visitedAt: string | null;
}

/** Ordered-from and visited markets, most recent activity (order or visit) first. */
export function mergeMyMarkets(recent: RecentMarket[], visits: MarketVisit[]): MyMarket[] {
  const visitedAt = new Map(visits.map((v) => [v.marketId, v.at]));
  const latest = (m: MyMarket) => Math.max(m.lastOrderAt === null ? 0 : Date.parse(m.lastOrderAt), m.visitedAt === null ? 0 : Date.parse(m.visitedAt));
  return recent
    .map((m) => ({ ...m, visitedAt: visitedAt.get(m.market.id) ?? null }))
    .sort((a, b) => latest(b) - latest(a));
}

/**
 * Start page view "Meine Märkte" next to list and map: the markets the user bought from, with what
 * they bought there (one tap back to a product), and markets visited on this device, so regular
 * shopping is two taps away. Guests see their visited markets.
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
  const { visits } = useVisitedMarkets();
  const signedIn = status === 'signedIn';
  const visited = visits.map((v) => v.marketId);
  const query = useRecentMarkets(visited, { lat: center.lat, lng: center.lng }, signedIn, signedIn || visited.length > 0);
  const items = mergeMyMarkets(query.data ?? [], visits);

  const empty = (
    <EmptyState icon="store" title={t('myMarkets.emptyTitle')} message={signedIn ? t('myMarkets.emptyMessage') : t('myMarkets.emptyGuest')} actionLabel={t('myMarkets.discover')} onAction={onDiscover} />
  );

  if (query.isPending && query.fetchStatus !== 'idle') {
    return (
      <View style={styles.flex}>
        {header}
        <CardSkeletons />
      </View>
    );
  }
  if (query.isError && items.length === 0) {
    return (
      <View style={styles.flex}>
        {header}
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      </View>
    );
  }
  return (
    <FlatList
      data={items}
      keyExtractor={(m) => m.market.id}
      ListHeaderComponent={header}
      ListEmptyComponent={empty}
      contentContainerStyle={styles.list}
      ItemSeparatorComponent={Separator}
      refreshControl={<RefreshControl refreshing={query.isRefetching} onRefresh={() => void query.refetch()} tintColor={colors.primary} />}
      testID="my-markets"
      renderItem={({ item }) => (
        <View style={styles.item} testID={`my-market-${item.market.id}`}>
          <MarketCard market={item.market} onPress={onOpenMarket} compact />
          <AppText variant="subhead" color="textSecondary">
            {item.lastOrderAt !== null
              ? t('myMarkets.lastOrder', { date: formatShortDate(item.lastOrderAt), count: item.orderCount })
              : item.visitedAt !== null
                ? t('myMarkets.lastVisit', { date: formatShortDate(item.visitedAt) })
                : ''}
          </AppText>
          {item.recentProducts.length > 0 && (
            <View style={styles.bought}>
              <AppText variant="headline" dense>
                {t('myMarkets.boughtHere')}
              </AppText>
              <FlatList
                horizontal
                data={item.recentProducts}
                keyExtractor={(p) => p.id}
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={styles.products}
                accessibilityLabel={t('myMarkets.boughtHere')}
                renderItem={({ item: product }) => (
                  <Pressable
                    onPress={() => onOpenProduct(item.market.id, product)}
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
          {item.lastOrderId !== null && (
            <Button
              title={t('myMarkets.lastOrderButton')}
              variant="secondary"
              icon="receipt"
              onPress={() => onOpenOrder(item.lastOrderId as string)}
              testID={`my-market-order-${item.market.id}`}
            />
          )}
        </View>
      )}
    />
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
}));
