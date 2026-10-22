import { useCallback, useMemo, useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { openMarket, openMarketProduct } from '../../../navigation/marketLinks';
import type { ShellTabScreenProps } from '../../../navigation/types';
import type { CodeName, MarketSummary } from '../../../shared/api';
import { HAMBURG, useChosenPlace } from '../../../shared/location';
import { makeStyles, useTheme } from '../../../shared/theme';
import {
  AppText,
  CardSkeletons,
  Chip,
  ChipScroller,
  EmptyState,
  ErrorState,
  IconButton,
  SegmentedControl,
} from '../../../shared/ui';
import { useCuisines, useMarkets, useMarketTypes } from '../api/hooks';
import { FilterSheet } from '../components/FilterSheet';
import { MarketCard } from '../components/MarketCard';
import { MarketMap } from '../components/MarketMap';
import { MyMarkets } from '../components/MyMarkets';
import { ShellHeader } from '../components/ShellHeader';
import { activeFilterCount, buildMarketQuery, DEFAULT_FILTERS, toggle, type MarketFilters } from '../model';

type Mode = 'list' | 'map' | 'mine';

function ChipRow({
  label,
  allLabel,
  items,
  selected,
  onToggle,
  onClear,
  testID,
}: {
  label: string;
  allLabel?: string;
  items: CodeName[];
  selected: string[];
  onToggle: (code: string) => void;
  onClear: () => void;
  testID: string;
}) {
  return (
    <ChipScroller accessibilityLabel={label} testID={testID}>
      {allLabel !== undefined && <Chip label={allLabel} selected={selected.length === 0} onPress={onClear} />}
      {items.map((item) => (
        <Chip
          key={item.code}
          label={item.name}
          selected={selected.includes(item.code)}
          onPress={() => onToggle(item.code)}
          testID={`chip-${item.code}`}
        />
      ))}
    </ChipScroller>
  );
}

/** Entdecken: markets near the chosen place, as a list or on a map, and "Meine Märkte" (ordered from or visited). */
export function DiscoverScreen({ navigation }: ShellTabScreenProps<'Discover'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { place } = useChosenPlace();
  const [filters, setFilters] = useState<MarketFilters>(DEFAULT_FILTERS);
  const [mode, setMode] = useState<Mode>('list');
  const [sheetOpen, setSheetOpen] = useState(false);
  const [sheetKey, setSheetKey] = useState(0);
  const types = useMarketTypes();
  const cuisines = useCuisines();
  const center = useMemo(() => place?.location ?? HAMBURG, [place]);
  const params = useMemo(() => buildMarketQuery(filters, center), [filters, center]);
  const query = useMarkets(params);
  const { markets } = query;
  const filterCount = activeFilterCount(filters);

  const open = useCallback((market: MarketSummary) => openMarket(navigation, market.id), [navigation]);
  const renderItem = useCallback(
    ({ item }: { item: MarketSummary }) => (
      <View style={styles.item}>
        <MarketCard market={item} onPress={open} />
      </View>
    ),
    [open, styles.item],
  );

  const header = (
    <View style={styles.controls}>
      {mode !== 'mine' && (
        <ChipRow
          label={t('discover.marketTypes')}
          allLabel={t('discover.all')}
          items={types.data ?? []}
          selected={filters.types}
          onToggle={(code) => setFilters((f) => ({ ...f, types: toggle(f.types, code) }))}
          onClear={() => setFilters((f) => ({ ...f, types: [] }))}
          testID="type-chips"
        />
      )}
      {mode !== 'mine' && (cuisines.data?.length ?? 0) > 0 && (
        <ChipRow
          label={t('discover.cuisines')}
          items={cuisines.data ?? []}
          selected={filters.cuisines}
          onToggle={(code) => setFilters((f) => ({ ...f, cuisines: toggle(f.cuisines, code) }))}
          onClear={() => setFilters((f) => ({ ...f, cuisines: [] }))}
          testID="cuisine-chips"
        />
      )}
      <View style={styles.modeRow}>
        <View style={styles.flex}>
          <SegmentedControl
            accessibilityLabel={t('discover.viewMode')}
            segments={[
              { value: 'list', label: t('discover.list'), icon: 'list' },
              { value: 'map', label: t('discover.map'), icon: 'map' },
              { value: 'mine', label: t('discover.mine'), icon: 'clock' },
            ]}
            value={mode}
            onChange={setMode}
          />
        </View>
        {mode !== 'mine' && (
          <View>
            <IconButton
              icon="filter"
              label={
                filterCount > 0
                  ? t('discover.filterButtonActive', { count: filterCount })
                  : t('discover.filterButton')
              }
              onPress={() => {
                setSheetKey((k) => k + 1);
                setSheetOpen(true);
              }}
              testID="filter-button"
              color={filterCount > 0 ? colors.accent : colors.text}
            />
          </View>
        )}
      </View>
    </View>
  );

  if (mode === 'mine') {
    return (
      <View style={styles.root}>
        <ShellHeader />
        <MyMarkets
          center={center}
          header={header}
          onOpenMarket={open}
          onOpenProduct={(marketId, product) => openMarketProduct(navigation, marketId, product.id)}
          onOpenOrder={(orderId) => navigation.navigate('OrderDetail', { orderId })}
          onDiscover={() => setMode('list')}
        />
      </View>
    );
  }

  let body;
  if (query.isPending) {
    body = <CardSkeletons />;
  } else if (query.isError && markets.length === 0) {
    body = <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  } else if (mode === 'map') {
    body = <MarketMap center={center} markets={markets} onOpen={open} />;
  } else {
    body = null;
  }

  return (
    <View style={styles.root}>
      <ShellHeader />
      {mode === 'list' && !query.isPending && !(query.isError && markets.length === 0) ? (
        <FlatList
          data={markets}
          keyExtractor={(m) => m.id}
          renderItem={renderItem}
          ListHeaderComponent={header}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={Separator}
          onEndReachedThreshold={0.5}
          onEndReached={() => {
            if (query.hasNextPage && !query.isFetchingNextPage) {
              void query.fetchNextPage();
            }
          }}
          refreshControl={
            <RefreshControl
              refreshing={query.isRefetching && !query.isFetchingNextPage}
              onRefresh={() => void query.refetch()}
              tintColor={colors.primary}
            />
          }
          ListEmptyComponent={
            <EmptyState
              icon="store"
              title={t('discover.emptyTitle')}
              message={t('discover.emptyMessage')}
              {...(filterCount > 0 || filters.types.length > 0 || filters.cuisines.length > 0
                ? { actionLabel: t('discover.reset'), onAction: () => setFilters(DEFAULT_FILTERS) }
                : {})}
            />
          }
          ListFooterComponent={
            query.isFetchingNextPage ? (
              <AppText variant="subhead" color="textSecondary" center style={styles.footer}>
                {t('common.loadingMore')}
              </AppText>
            ) : undefined
          }
          testID="market-list"
        />
      ) : (
        <View style={styles.flex}>
          {header}
          {body}
        </View>
      )}
      <FilterSheet
        key={sheetKey}
        visible={sheetOpen}
        filters={filters}
        onClose={() => setSheetOpen(false)}
        onApply={(next) => {
          setFilters(next);
          setSheetOpen(false);
        }}
      />
    </View>
  );
}

function Separator() {
  return <View style={{ height: 28 }} />;
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  flex: { flex: 1 },
  controls: { gap: t.spacing.sm, paddingBottom: t.spacing.md },
  modeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: t.spacing.sm,
    paddingHorizontal: t.layout.gutter,
  },
  list: { paddingBottom: t.spacing.xxxl },
  item: { paddingHorizontal: t.layout.gutter },
  footer: { padding: t.spacing.lg },
}));
