import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketStackScreenProps } from '../../../navigation/types';
import type { ProductSort } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { CardSkeletons, Chip, ChipScroller, EmptyState, ErrorState, Screen, ScreenHeader } from '../../../shared/ui';
import { useMarketContext } from '../../market/MarketContext';
import { useMarketProducts } from '../api/hooks';
import { ProductGrid } from '../components/ProductGrid';

export const PRODUCT_SORTS: ProductSort[] = ['relevance', 'price_asc', 'price_desc', 'name'];

/** Product list for a category, a banner or a quick link (offers, new, organic, best sellers). */
export function CategoryProductsScreen({ route, navigation }: MarketStackScreenProps<'MarketProducts'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = useMarketContext();
  const [sort, setSort] = useState<ProductSort>('relevance');
  const query = useMarketProducts(marketId, { ...route.params.filter, sort, size: 20 });

  return (
    <Screen padded={false} header={<ScreenHeader title={route.params.title} onBack={() => navigation.goBack()} />}>
      {query.isPending ? (
        <CardSkeletons count={2} />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : (
        <ProductGrid
          marketId={marketId}
          products={query.products}
          header={
            // Best sellers come in their rank order; sorting them would hide that.
            route.params.filter.bestsellersOnly === true ? undefined : (
              <View style={styles.header}>
                <ChipScroller accessibilityLabel={t('marketSearch.sortLabel')}>
                  {PRODUCT_SORTS.map((s) => (
                    <Chip key={s} label={t(`marketSearch.sort.${s}`)} selected={sort === s} onPress={() => setSort(s)} />
                  ))}
                </ChipScroller>
              </View>
            )
          }
          empty={<EmptyState icon="package" title={t('marketSearch.noProductsTitle')} message={t('marketSearch.noProductsMessage')} />}
          onEndReached={() => {
            if (query.hasNextPage && !query.isFetchingNextPage) {
              void query.fetchNextPage();
            }
          }}
          loadingMore={query.isFetchingNextPage}
          refreshing={query.isRefetching && !query.isFetchingNextPage}
          onRefresh={() => void query.refetch()}
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  header: { marginHorizontal: -t.layout.gutter, marginBottom: t.spacing.lg },
}));
