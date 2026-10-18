import { useCallback, type ReactElement } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, View, type ListRenderItem } from 'react-native';

import type { ProductSummary } from '../../../shared/api';
import { colors, spacing } from '../../../shared/theme';
import { ProductTile } from '../../../shared/ui';
import { useCartQuantities, useCartUpdater } from '../../cart/api/hooks';
import { useRootNavigation } from '../../../navigation/types';

interface ProductGridProps {
  products: readonly ProductSummary[];
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  fetchNextPage: () => unknown;
  refreshing: boolean;
  onRefresh: () => void;
  ListEmptyComponent?: ReactElement;
  ListHeaderComponent?: ReactElement;
}

const keyExtractor = (item: ProductSummary) => item.id;

/** Two-column, infinitely scrolling product list shared by category and search screens. */
export function ProductGrid({
  products,
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage,
  refreshing,
  onRefresh,
  ListEmptyComponent,
  ListHeaderComponent,
}: ProductGridProps) {
  const navigation = useRootNavigation();
  const quantities = useCartQuantities();
  const { onChangeQuantity, pendingProductId } = useCartUpdater();

  const openProduct = useCallback(
    (productId: string) => navigation.navigate('ProductDetail', { productId }),
    [navigation],
  );

  const renderItem = useCallback<ListRenderItem<ProductSummary>>(
    ({ item }) => (
      <ProductTile
        product={item}
        quantity={quantities.get(item.id) ?? 0}
        busy={pendingProductId === item.id}
        onPress={openProduct}
        onChangeQuantity={onChangeQuantity}
      />
    ),
    [quantities, pendingProductId, openProduct, onChangeQuantity],
  );

  const onEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      void fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <FlatList
      data={products}
      keyExtractor={keyExtractor}
      renderItem={renderItem}
      numColumns={2}
      contentContainerStyle={styles.content}
      onEndReached={onEndReached}
      onEndReachedThreshold={0.5}
      refreshing={refreshing}
      onRefresh={onRefresh}
      ListHeaderComponent={ListHeaderComponent}
      ListEmptyComponent={ListEmptyComponent}
      ListFooterComponent={
        isFetchingNextPage ? (
          <View style={styles.footer}>
            <ActivityIndicator color={colors.primary} accessibilityLabel="Loading more products" />
          </View>
        ) : undefined
      }
      initialNumToRender={8}
      windowSize={7}
      removeClippedSubviews
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.sm, flexGrow: 1 },
  footer: { padding: spacing.lg },
});
