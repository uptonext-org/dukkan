import { EmptyState, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useProductPages } from '../api/hooks';
import { ProductGrid } from '../components/ProductGrid';

export function CategoryProductsScreen({ route }: RootStackScreenProps<'CategoryProducts'>) {
  const products = useProductPages({ categoryId: route.params.categoryId });

  const state = queryState(products);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }
  return (
    <Screen padded={false}>
      <ProductGrid
        products={products.data ?? []}
        hasNextPage={products.hasNextPage}
        isFetchingNextPage={products.isFetchingNextPage}
        fetchNextPage={products.fetchNextPage}
        refreshing={products.isRefetching && !products.isFetchingNextPage}
        onRefresh={() => void products.refetch()}
        ListEmptyComponent={<EmptyState title="No products here yet" message="Try another category." />}
      />
    </Screen>
  );
}
