import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import type { ProductSort } from '../../../shared/api';
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { colors, spacing, touchTarget, typography } from '../../../shared/theme';
import { Chip, EmptyState, ErrorState, LoadingState, Screen, TextField } from '../../../shared/ui';
import type { TabScreenProps } from '../../../navigation/types';
import { useProductPages } from '../../catalog/api/hooks';
import { ProductGrid } from '../../catalog/components/ProductGrid';
import { useSuggestions } from '../api/hooks';

const SORT_OPTIONS: { value: ProductSort; label: string }[] = [
  { value: 'relevance', label: 'Relevance' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'name', label: 'Name' },
];

export const SEARCH_DEBOUNCE_MS = 300;

export function SearchScreen({ route }: TabScreenProps<'Search'>) {
  const [text, setText] = useState('');
  const [focused, setFocused] = useState(false);
  const [sort, setSort] = useState<ProductSort>('relevance');
  const [promotedOnly, setPromotedOnly] = useState(route.params?.promotedOnly ?? false);
  const query = useDebouncedValue(text.trim(), SEARCH_DEBOUNCE_MS);

  const routePromotedOnly = route.params?.promotedOnly;
  useEffect(() => {
    if (routePromotedOnly !== undefined) {
      // Sync when another screen links here with a filter (e.g. "All offers").
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPromotedOnly(routePromotedOnly);
    }
  }, [routePromotedOnly]);

  const suggestions = useSuggestions(query, focused);
  const searchActive = query.length > 0 || promotedOnly;
  const results = useProductPages(
    { q: query.length > 0 ? query.slice(0, 100) : undefined, sort, promotedOnly: promotedOnly || undefined },
    searchActive,
  );
  const showSuggestions = focused && (suggestions.data?.length ?? 0) > 0 && text.trim().length >= 2;

  const renderResults = () => {
    if (!searchActive) {
      return <EmptyState title="Find your groceries" message="Search by product name, brand or keyword." />;
    }
    if (results.isPending) {
      return <LoadingState label="Searching" />;
    }
    if (results.isError) {
      return <ErrorState error={results.error} onRetry={() => void results.refetch()} />;
    }
    return (
      <ProductGrid
        products={results.data}
        hasNextPage={results.hasNextPage}
        isFetchingNextPage={results.isFetchingNextPage}
        fetchNextPage={results.fetchNextPage}
        refreshing={results.isRefetching && !results.isFetchingNextPage}
        onRefresh={() => void results.refetch()}
        ListEmptyComponent={
          <EmptyState
            title="No results"
            message={query.length > 0 ? `We couldn't find anything for "${query}".` : 'No offers right now.'}
          />
        }
      />
    );
  };

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <TextField
          label="Search products"
          value={text}
          onChangeText={setText}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          placeholder="e.g. oat milk"
          autoCorrect={false}
          returnKeyType="search"
          clearButtonMode="while-editing"
          maxLength={100}
        />
        {showSuggestions && (
          <View style={styles.suggestions} accessibilityLabel="Suggestions">
            {suggestions.data?.map((suggestion) => (
              <Pressable
                key={suggestion}
                onPress={() => {
                  setText(suggestion);
                  setFocused(false);
                }}
                accessibilityRole="button"
                accessibilityLabel={`Search for ${suggestion}`}
                style={({ pressed }) => [styles.suggestion, pressed && styles.suggestionPressed]}>
                <Text style={styles.suggestionText}>{suggestion}</Text>
              </Pressable>
            ))}
          </View>
        )}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          accessibilityRole="radiogroup"
          accessibilityLabel="Sort by">
          {SORT_OPTIONS.map((option) => (
            <Chip
              key={option.value}
              label={option.label}
              selected={sort === option.value}
              onPress={() => setSort(option.value)}
            />
          ))}
        </ScrollView>
        <View style={styles.toggleRow}>
          <Text style={styles.toggleLabel} nativeID="promotedOnlyLabel">
            Only offers
          </Text>
          <Switch
            value={promotedOnly}
            onValueChange={setPromotedOnly}
            accessibilityLabel="Only offers"
            accessibilityRole="switch"
            trackColor={{ true: colors.primary, false: colors.border }}
          />
        </View>
      </View>
      <View style={styles.results}>{renderResults()}</View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { paddingHorizontal: spacing.lg, paddingTop: spacing.md, backgroundColor: colors.background },
  suggestions: {
    backgroundColor: colors.surface,
    borderRadius: 8,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    marginTop: -spacing.md,
    marginBottom: spacing.md,
  },
  suggestion: { minHeight: touchTarget, justifyContent: 'center', paddingHorizontal: spacing.md },
  suggestionPressed: { backgroundColor: colors.primaryMuted },
  suggestionText: { ...typography.body, color: colors.text },
  toggleRow: {
    minHeight: touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginVertical: spacing.sm,
  },
  toggleLabel: { ...typography.body, color: colors.text },
  results: { flex: 1 },
});
