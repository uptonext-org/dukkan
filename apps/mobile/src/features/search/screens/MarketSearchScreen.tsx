import { useNavigation, type NavigationProp } from '@react-navigation/native';
import { useMemo, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../../../shared/format';
import type { MarketStackParamList } from '../../../navigation/types';
import type { ProductSort } from '../../../shared/api';
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { makeStyles, tintColor, useTheme, type TileTint } from '../../../shared/theme';
import { AppText, CardSkeletons, Chip, ChipScroller, EmptyState, ErrorState, Icon, ImageTile, ListRow, SearchField, type IconName } from '../../../shared/ui';
import { useCategories, useMarketProducts, useSuggestions } from '../../catalog/api/hooks';
import { ProductGrid } from '../../catalog/components/ProductGrid';
import { PRODUCT_SORTS } from '../../catalog/screens/CategoryProductsScreen';
import { useMarketContext } from '../../market/MarketContext';

interface QuickLink {
  key: 'offers' | 'new' | 'organic';
  icon: IconName;
  tint: TileTint | 'promo';
}

/**
 * Quick links with 72 pt tinted tiles (design-system.md §4.3): "Alle Angebote" on the yellow
 * `promo` tile with a %, "Neu im Markt" pink with sparkles, then "Bio".
 * The design also shows "Regional", but `/markets/{id}/products` has `organicOnly` and no regional
 * filter (REGIONAL exists only as a market tag), so there is no "Regional" link: we do not invent
 * a client-side filter the API cannot answer. Add it once the contract has a product filter.
 */
const QUICK_LINKS: QuickLink[] = [
  { key: 'offers', icon: 'percent', tint: 'promo' },
  { key: 'new', icon: 'sparkles', tint: 'blush' },
  { key: 'organic', icon: 'leaf', tint: 'sage' },
];

/** Market search: field with suggestions, quick links, all categories, results grid with sort/filter. */
export function MarketSearchScreen() {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const navigation = useNavigation<NavigationProp<MarketStackParamList>>();
  const { marketId } = useMarketContext();
  const [text, setText] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [sort, setSort] = useState<ProductSort>('relevance');
  const [organicOnly, setOrganicOnly] = useState(false);
  const [promotedOnly, setPromotedOnly] = useState(false);
  const typed = useDebouncedValue(text.trim(), 250);
  const suggestions = useSuggestions(marketId, typed);
  const categories = useCategories(marketId);
  const params = useMemo(
    () => ({ q: submitted, sort, size: 20, ...(organicOnly ? { organicOnly } : {}), ...(promotedOnly ? { promotedOnly } : {}) }),
    [submitted, sort, organicOnly, promotedOnly],
  );
  const results = useMarketProducts(marketId, params, submitted.length >= 1);
  const showSuggestions = text.trim().length >= 2 && text.trim() !== submitted && (suggestions.data?.length ?? 0) > 0;

  const quickTitle = (key: QuickLink['key']) =>
    key === 'offers' ? t('marketSearch.allOffers') : key === 'new' ? t('marketSearch.newInMarket') : t('marketSearch.organic');
  const openQuick = (key: QuickLink['key']) =>
    navigation.navigate('MarketProducts', {
      title: quickTitle(key),
      filter: key === 'offers' ? { promotedOnly: true } : key === 'new' ? { newOnly: true } : { organicOnly: true },
    });

  const submit = (value: string) => {
    setText(value);
    setSubmitted(value.trim());
  };

  const field = (
    <View style={styles.field}>
      <SearchField
        value={text}
        onChangeText={(value) => {
          setText(value);
          if (value.trim() === '') {
            setSubmitted('');
          }
        }}
        onSubmitEditing={() => submit(text)}
        placeholder={t('marketSearch.placeholder')}
        testID="market-search-field"
      />
      {showSuggestions && (
        <View style={styles.suggestions} accessibilityLabel={t('marketSearch.suggestions')}>
          {(suggestions.data ?? []).map((s) => (
            <ListRow key={s} title={s} icon="search" chevron={false} onPress={() => submit(s)} testID="search-suggestion" />
          ))}
        </View>
      )}
    </View>
  );

  if (submitted.length >= 1) {
    const filters = (
      <ChipScroller style={styles.chipsWrap} accessibilityLabel={t('marketSearch.sortLabel')}>
        {PRODUCT_SORTS.map((s) => (
          <Chip key={s} label={t(`marketSearch.sort.${s}`)} selected={sort === s} onPress={() => setSort(s)} />
        ))}
        <Chip label={t('marketSearch.organic')} icon="leaf" selected={organicOnly} onPress={() => setOrganicOnly((v) => !v)} />
        <Chip label={t('marketSearch.onlyOffers')} icon="percent" selected={promotedOnly} onPress={() => setPromotedOnly((v) => !v)} />
      </ChipScroller>
    );
    return (
      <View style={styles.root}>
        {field}
        {filters}
        {results.isPending ? (
          <CardSkeletons count={2} />
        ) : results.isError ? (
          <ErrorState error={results.error} onRetry={() => void results.refetch()} />
        ) : (
          <ProductGrid
            marketId={marketId}
            products={results.products}
            header={
              <AppText variant="subhead" color="textSecondary" style={styles.count} accessibilityLiveRegion="polite">
                {t('marketSearch.resultCount', { count: results.data.pages[0]?.totalItems ?? 0 })}
              </AppText>
            }
            empty={<EmptyState icon="search" title={t('marketSearch.noResultsTitle', { query: submitted })} message={t('marketSearch.noResultsMessage')} />}
            onEndReached={() => {
              if (results.hasNextPage && !results.isFetchingNextPage) {
                void results.fetchNextPage();
              }
            }}
            loadingMore={results.isFetchingNextPage}
            testID="market-search-results"
          />
        )}
      </View>
    );
  }

  const topCategories = (categories.data ?? []).filter((c) => c.parentId == null).sort((a, b) => a.sortOrder - b.sortOrder);
  return (
    <View style={styles.root}>
      {field}
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {QUICK_LINKS.map((link) => (
          <ListRow
            key={link.key}
            title={quickTitle(link.key)}
            leading={
              <View style={[styles.quickTile, { backgroundColor: link.tint === 'promo' ? colors.promo : tintColor(colors, link.tint) }]}>
                <Icon name={link.icon} size={32} color={link.key === 'offers' ? colors.onPromo : link.key === 'new' ? colors.accent : colors.primary} />
              </View>
            }
            onPress={() => openQuick(link.key)}
            testID={`quick-${link.key}`}
          />
        ))}
        <View style={styles.separator} />
        <AppText variant="callout" color="textSecondary" accessibilityRole="header" style={styles.heading}>
          {t('marketSearch.allCategories')}
        </AppText>
        {categories.isError && <ErrorState error={categories.error} onRetry={() => void categories.refetch()} />}
        {topCategories.map((category) => (
          <ListRow
            key={category.id}
            title={category.name}
            leading={<ImageTile uri={category.imageUrl} size={72} radius={12} tint="sage" />}
            {...(category.productCount != null ? { accessibilityLabel: forSpeech(`${category.name}, ${t('marketSearch.productCount', { count: category.productCount })}`) } : {})}
            onPress={() => navigation.navigate('MarketProducts', { title: category.name, filter: { categoryId: category.id } })}
            testID={`category-${category.id}`}
          />
        ))}
      </ScrollView>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  field: { paddingHorizontal: t.layout.gutter, paddingTop: t.spacing.sm, paddingBottom: t.spacing.sm },
  suggestions: { marginTop: t.spacing.xs },
  content: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl },
  quickTile: { width: 72, height: 72, borderRadius: t.radii.pill, alignItems: 'center', justifyContent: 'center' },
  separator: { height: 1, backgroundColor: t.colors.separator, marginVertical: t.spacing.md },
  heading: { marginBottom: t.spacing.sm },
  chipsWrap: { marginBottom: t.spacing.md },
  count: { marginBottom: t.spacing.md },
}));
