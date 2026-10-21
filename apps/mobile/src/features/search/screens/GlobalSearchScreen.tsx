import { useMemo, useState } from 'react';
import { FlatList, Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { openMarketProduct } from '../../../navigation/marketLinks';
import type { ShellTabScreenProps } from '../../../navigation/types';
import type { MarketOffer, OfferSort } from '../../../shared/api';
import { formatDistance, formatMoney, formatMoneyForAccessibility, forSpeech } from '../../../shared/format';
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { HAMBURG, useChosenPlace } from '../../../shared/location';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, CardSkeletons, Chip, ChipScroller, EmptyState, ErrorState, Icon, ImageTile, SearchField, placeholderProps } from '../../../shared/ui';
import { ShellHeader } from '../../marketplace/components/ShellHeader';
import { useNearbyOffers } from '../api/hooks';

const SORTS: OfferSort[] = ['relevance', 'price_asc', 'distance'];

export interface OfferGroup {
  key: string;
  name: string;
  imageUrl: string | null | undefined;
  iconKey: string | null | undefined;
  offers: MarketOffer[];
}

/**
 * Groups offers by shared catalog product (falls back to the name), keeping server order, so one
 * product shows with the nearby markets that sell it (design-system.md §4.2 › Suchen).
 */
export function groupOffers(offers: readonly MarketOffer[]): OfferGroup[] {
  const groups = new Map<string, OfferGroup>();
  for (const offer of offers) {
    const key = offer.product.catalogProductId ?? offer.product.name.toLowerCase();
    let group = groups.get(key);
    if (!group) {
      group = { key, name: offer.product.name, imageUrl: offer.product.imageUrl, iconKey: offer.product.iconKey, offers: [] };
      groups.set(key, group);
    }
    group.offers.push(offer);
  }
  return [...groups.values()];
}

/** Suchen (marketplace): products across nearby markets with price and distance. */
export function GlobalSearchScreen({ navigation }: ShellTabScreenProps<'GlobalSearch'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { place } = useChosenPlace();
  const [text, setText] = useState('');
  const [sort, setSort] = useState<OfferSort>('relevance');
  const q = useDebouncedValue(text.trim(), 350);
  const at = place?.location ?? HAMBURG;
  const query = useNearbyOffers({ q, lat: at.lat, lng: at.lng, sort, size: 30 });
  const groups = useMemo(() => groupOffers(query.offers), [query.offers]);

  let body;
  if (q.length < 2) {
    body = <EmptyState icon="search" title={t('globalSearch.introTitle')} message={t('globalSearch.introMessage')} />;
  } else if (query.isPending) {
    body = <CardSkeletons count={3} height={120} />;
  } else if (query.isError) {
    body = <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  } else {
    body = (
      <FlatList
        data={groups}
        keyExtractor={(g) => g.key}
        contentContainerStyle={styles.list}
        keyboardShouldPersistTaps="handled"
        onEndReachedThreshold={0.5}
        onEndReached={() => {
          if (query.hasNextPage && !query.isFetchingNextPage) {
            void query.fetchNextPage();
          }
        }}
        ListEmptyComponent={<EmptyState icon="search" title={t('globalSearch.noResultsTitle', { query: q })} message={t('globalSearch.noResultsMessage')} />}
        testID="global-search-results"
        renderItem={({ item: group }) => (
          <View style={styles.group}>
            <View style={styles.groupHeader}>
              <ImageTile uri={group.imageUrl} size={56} radius={12} {...placeholderProps(group.iconKey)} />
              <AppText variant="headline" accessibilityRole="header" style={styles.flex}>
                {group.name}
              </AppText>
            </View>
            {group.offers.map((offer) => {
              const { product, market } = offer;
              const price = product.promotionalPrice ?? product.price;
              const distance = market.distanceMeters != null ? formatDistance(market.distanceMeters) : undefined;
              return (
                <Pressable
                  key={`${market.id}-${product.id}`}
                  onPress={() => openMarketProduct(navigation, market.id, product.id)}
                  accessibilityRole="button"
                  accessibilityLabel={forSpeech([product.name, product.unitSize, market.name, distance, formatMoneyForAccessibility(price), product.promotion?.label].filter(Boolean).join(', '))}
                  accessibilityHint={t('globalSearch.openHint')}
                  style={({ pressed }) => [styles.offer, pressed && styles.pressed]}
                  testID={`offer-${product.id}`}>
                  <View style={styles.flex}>
                    <AppText variant="body" dense numberOfLines={1}>
                      {market.name}
                    </AppText>
                    <AppText variant="subhead" color="textSecondary" dense>
                      {[product.unitSize, distance].filter(Boolean).join(' · ')}
                    </AppText>
                  </View>
                  <View style={styles.priceCol}>
                    <AppText variant="price" color={product.promotionalPrice != null ? 'accent' : 'text'} dense>
                      {formatMoney(price)}
                    </AppText>
                    {product.promotionalPrice != null && (
                      <AppText variant="caption" color="textSecondary" strike dense>
                        {formatMoney(product.price)}
                      </AppText>
                    )}
                  </View>
                  <Icon name="chevron" size={18} color={colors.textSecondary} />
                </Pressable>
              );
            })}
          </View>
        )}
      />
    );
  }

  return (
    <View style={styles.root}>
      <ShellHeader />
      <View style={styles.field}>
        <SearchField value={text} onChangeText={setText} placeholder={t('globalSearch.placeholder')} testID="global-search-field" />
      </View>
      <ChipScroller style={styles.chipsWrap} accessibilityLabel={t('globalSearch.sortLabel')} testID="offer-sorts">
        {SORTS.map((s) => (
          <Chip key={s} label={t(`globalSearch.sort.${s}`)} selected={sort === s} onPress={() => setSort(s)} testID={`offer-sort-${s}`} />
        ))}
      </ChipScroller>
      {body}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  field: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.sm },
  chipsWrap: { marginBottom: t.spacing.sm },
  list: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl, flexGrow: 1 },
  group: { paddingVertical: t.spacing.md, borderBottomWidth: 1, borderBottomColor: t.colors.separator },
  groupHeader: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, marginBottom: t.spacing.xs },
  offer: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, minHeight: 56, paddingStart: 68 },
  pressed: { opacity: 0.6 },
  flex: { flex: 1 },
  priceCol: { alignItems: 'flex-end' },
}));
