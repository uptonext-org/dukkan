import { FlatList, View } from 'react-native';

import type { ProductSummary } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { ProductTile } from '../../../shared/ui';
import { useProductActions } from '../useProductActions';

/** Horizontal section of product tiles. Starts at the start edge in RTL (Fabric mirrors the scroll view). */
export function ProductCarousel({ marketId, products, label }: { marketId: string; products: ProductSummary[]; label: string }) {
  const styles = useStyles();
  const actions = useProductActions(marketId);
  return (
    <FlatList
      horizontal
      data={products}
      keyExtractor={(p) => p.id}
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.content}
      accessibilityLabel={label}
      renderItem={({ item }) => (
        <View>
          <ProductTile
            product={item}
            width={168}
            quantity={actions.quantities.get(item.id) ?? 0}
            busy={actions.busyProductId === item.id}
            onPress={actions.openProduct}
            onChangeQuantity={actions.changeQuantity}
          />
        </View>
      )}
    />
  );
}

const useStyles = makeStyles((t) => ({
  content: { paddingHorizontal: t.layout.gutter, gap: t.spacing.md },
}));
