import { useCallback, type ReactElement } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { ProductSummary } from '../../../shared/api';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, ProductTile } from '../../../shared/ui';
import { useProductActions } from '../useProductActions';

interface ProductGridProps {
  marketId: string;
  products: ProductSummary[];
  header?: ReactElement | undefined;
  empty?: ReactElement;
  onEndReached?: () => void;
  loadingMore?: boolean;
  refreshing?: boolean;
  onRefresh?: () => void;
  showFavorite?: boolean;
  testID?: string;
}

/** Two-column grid of product tiles; rows mirror automatically in RTL. */
export function ProductGrid({ marketId, products, header, empty, onEndReached, loadingMore = false, refreshing, onRefresh, showFavorite = true, testID }: ProductGridProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const actions = useProductActions(marketId);
  const renderItem = useCallback(
    ({ item }: { item: ProductSummary }) => (
      <View style={styles.cell}>
        <ProductTile
          product={item}
          quantity={actions.quantities.get(item.id) ?? 0}
          busy={actions.busyProductId === item.id}
            orderable={actions.orderable}
          onPress={actions.openProduct}
          onChangeQuantity={actions.changeQuantity}
          {...(showFavorite ? { onToggleFavorite: actions.toggleFavorite } : {})}
        />
      </View>
    ),
    [actions, showFavorite, styles.cell],
  );
  return (
    <FlatList
      data={products}
      keyExtractor={(p) => p.id}
      numColumns={2}
      renderItem={renderItem}
      columnWrapperStyle={styles.columns}
      contentContainerStyle={styles.content}
      {...(header !== undefined ? { ListHeaderComponent: header } : {})}
      {...(empty !== undefined ? { ListEmptyComponent: empty } : {})}
      onEndReachedThreshold={0.5}
      {...(onEndReached !== undefined ? { onEndReached } : {})}
      {...(onRefresh !== undefined
        ? { refreshControl: <RefreshControl refreshing={refreshing ?? false} onRefresh={onRefresh} tintColor={colors.primary} /> }
        : {})}
      ListFooterComponent={
        loadingMore ? (
          <AppText variant="subhead" color="textSecondary" center style={styles.footer}>
            {t('common.loadingMore')}
          </AppText>
        ) : undefined
      }
      keyboardShouldPersistTaps="handled"
      testID={testID}
    />
  );
}

const useStyles = makeStyles((t) => ({
  content: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl, flexGrow: 1 },
  columns: { gap: t.spacing.md, marginBottom: t.spacing.xxl },
  cell: { flex: 1, maxWidth: '50%' },
  footer: { padding: t.spacing.lg },
}));
