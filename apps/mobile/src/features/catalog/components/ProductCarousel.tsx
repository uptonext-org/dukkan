import { FlatList, View } from 'react-native';

import type { ProductSummary } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { ProductTile } from '../../../shared/ui';
import { useProductActions } from '../useProductActions';

/** Horizontal section of product tiles. Starts at the start edge in RTL (Fabric mirrors the scroll view). */
export function ProductCarousel({ marketId, products, label }: { marketId: string; products: ProductSummary[]; label: string }) {
  const styles = useStyles();
  const actions = useProductActions(marketId);
  // Keep a tagline line in every tile of the row when any product has one, so names line up.
  const reserveTagline = products.some((p) => p.tagline != null && p.tagline !== '');
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
            reserveTagline={reserveTagline}
            quantity={actions.quantities.get(item.id) ?? 0}
            busy={actions.busyProductId === item.id}
            orderable={actions.orderable}
            onPress={actions.openProduct}
            onChangeQuantity={actions.changeQuantity}
            onToggleFavorite={actions.toggleFavorite}
          />
        </View>
      )}
    />
  );
}

const useStyles = makeStyles((t) => ({
  content: { paddingHorizontal: t.layout.gutter, gap: t.spacing.md },
}));
