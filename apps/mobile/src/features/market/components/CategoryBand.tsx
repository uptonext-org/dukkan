import { useTranslation } from 'react-i18next';

import type { Category } from '../../../shared/api';
import { Chip, ChipScroller, productPlaceholder } from '../../../shared/ui';
import { useCategories } from '../../catalog/api/hooks';

/** Top-level categories of this market that have products, in the market's order. */
export function bandCategories(categories: Category[]): Category[] {
  return categories
    .filter((c) => c.parentId == null && (c.productCount == null || c.productCount > 0))
    .sort((a, b) => a.sortOrder - b.sortOrder);
}

/**
 * The band of categories at the top of a market (Obst, Gemüse, Fleisch …), right below the
 * delivery time. A tap opens that category's products. Nothing is shown while loading or when the
 * market has no categories yet, so the page does not jump.
 */
export function CategoryBand({ marketId, onOpen }: { marketId: string; onOpen: (category: Category) => void }) {
  const { t } = useTranslation();
  const { data } = useCategories(marketId);
  const categories = bandCategories(data ?? []);
  if (categories.length === 0) {
    return null;
  }
  return (
    <ChipScroller accessibilityLabel={t('marketHome.categories')} testID="category-band">
      {categories.map((category) => (
        <Chip
          key={category.id}
          label={category.name}
          icon={productPlaceholder(category.iconKey).icon}
          onPress={() => onOpen(category)}
          accessibilityHint={t('marketHome.categoryHint')}
          testID={`category-chip-${category.slug}`}
        />
      ))}
    </ChipScroller>
  );
}
