import { memo, useCallback, useMemo } from 'react';
import { FlatList, StyleSheet, Text, View, type ListRenderItem } from 'react-native';

import type { ProductSummary } from '../../../shared/api';
import { colors, spacing, typography } from '../../../shared/theme';
import { Button, EmptyState, ListRow, ProductTile, Screen, queryState } from '../../../shared/ui';
import { useRootNavigation, type TabScreenProps } from '../../../navigation/types';
import { useCartQuantities, useCartUpdater } from '../../cart/api/hooks';
import { useCategories, useProductPages } from '../api/hooks';
import { flattenCategoryTree, type CategoryRow } from '../model';

const OFFER_TILE_WIDTH = 168;
const OFFER_TILE_SPACING = spacing.xs * 2;

const CategoryItem = memo(function CategoryItem({
  row,
  onOpen,
}: {
  row: CategoryRow;
  onOpen: (row: CategoryRow) => void;
}) {
  return (
    <ListRow
      title={row.category.name}
      indent={row.depth * spacing.lg}
      onPress={() => onOpen(row)}
      accessibilityHint={`Shows products in ${row.category.name}`}
    />
  );
});

function PromotedProducts() {
  const offers = useProductPages({ promotedOnly: true, size: 10 });
  const quantities = useCartQuantities();
  const { onChangeQuantity, pendingProductId } = useCartUpdater();
  const navigation = useRootNavigation();

  const openProduct = useCallback(
    (productId: string) => navigation.navigate('ProductDetail', { productId }),
    [navigation],
  );
  const renderItem = useCallback<ListRenderItem<ProductSummary>>(
    ({ item }) => (
      <ProductTile
        product={item}
        width={OFFER_TILE_WIDTH}
        quantity={quantities.get(item.id) ?? 0}
        busy={pendingProductId === item.id}
        onPress={openProduct}
        onChangeQuantity={onChangeQuantity}
      />
    ),
    [quantities, pendingProductId, openProduct, onChangeQuantity],
  );

  if (offers.isPending || offers.isError || offers.data.length === 0) {
    return null;
  }
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle} accessibilityRole="header">
        Offers this week
      </Text>
      <FlatList
        horizontal
        data={offers.data}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        showsHorizontalScrollIndicator={false}
        getItemLayout={(_data, index) => ({
          length: OFFER_TILE_WIDTH + OFFER_TILE_SPACING,
          offset: (OFFER_TILE_WIDTH + OFFER_TILE_SPACING) * index,
          index,
        })}
        contentContainerStyle={styles.offers}
      />
    </View>
  );
}

export function HomeScreen({ navigation }: TabScreenProps<'Home'>) {
  const categories = useCategories();
  const rows = useMemo(() => flattenCategoryTree(categories.data ?? []), [categories.data]);

  const openCategory = useCallback(
    (row: CategoryRow) =>
      navigation.navigate('CategoryProducts', { categoryId: row.category.id, title: row.category.name }),
    [navigation],
  );
  const renderItem = useCallback<ListRenderItem<CategoryRow>>(
    ({ item }) => <CategoryItem row={item} onOpen={openCategory} />,
    [openCategory],
  );

  const state = queryState(categories);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={rows}
        keyExtractor={(row) => row.category.id}
        renderItem={renderItem}
        refreshing={categories.isRefetching}
        onRefresh={() => void categories.refetch()}
        ListHeaderComponent={
          <View>
            <PromotedProducts />
            <View style={styles.section}>
              <Button
                title="See all current offers"
                variant="secondary"
                onPress={() => navigation.navigate('Promotions')}
              />
            </View>
            <Text style={[styles.sectionTitle, styles.categoriesTitle]} accessibilityRole="header">
              Categories
            </Text>
          </View>
        }
        ListEmptyComponent={<EmptyState title="No categories yet" message="Please check back later." />}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  section: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg },
  sectionTitle: { ...typography.heading, color: colors.text, marginBottom: spacing.sm },
  categoriesTitle: { paddingHorizontal: spacing.lg, paddingTop: spacing.xl },
  offers: { paddingRight: spacing.lg },
});
