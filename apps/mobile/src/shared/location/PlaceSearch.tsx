import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { queryKeys, useApi, type Place } from '../api';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useLang } from '../i18n/useLang';
import { useTheme } from '../theme';
import { AppText, ListRow, SearchField } from '../ui';

/** Debounced address autocomplete over /geo/search (phase 1: built-in Hamburg gazetteer). */
export function PlaceSearch({
  onSelect,
  placeholder,
  autoFocus = false,
}: {
  onSelect: (place: Place) => void;
  placeholder?: string;
  autoFocus?: boolean;
}) {
  const api = useApi();
  const lang = useLang();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const [text, setText] = useState('');
  const q = useDebouncedValue(text.trim(), 300);
  const enabled = q.length >= 2;
  const query = useQuery({
    queryKey: queryKeys.places(lang, q),
    queryFn: ({ signal }) => api.searchPlaces(q, 5, signal),
    enabled,
    staleTime: 5 * 60_000,
  });
  return (
    <View>
      <SearchField
        value={text}
        onChangeText={setText}
        placeholder={placeholder ?? t('onboarding.searchPlaceholder')}
        autoFocus={autoFocus}
        testID="place-search"
      />
      {enabled && query.isFetching && <ActivityIndicator style={{ marginTop: 12 }} color={colors.primary} />}
      {enabled && query.isSuccess && query.data.length === 0 && (
        <AppText variant="subhead" color="textSecondary" style={{ marginTop: 12 }}>
          {t('onboarding.noPlaces')}
        </AppText>
      )}
      {enabled && query.isError && (
        <AppText variant="subhead" color="accent" style={{ marginTop: 12 }} accessibilityRole="alert">
          {t('onboarding.searchFailed')}
        </AppText>
      )}
      {enabled &&
        (query.data ?? []).map((place) => (
          <ListRow
            key={`${place.label}-${place.location.lat}-${place.location.lng}`}
            title={place.label}
            icon="pin"
            chevron={false}
            onPress={() => {
              setText('');
              onSelect(place);
            }}
            testID="place-suggestion"
          />
        ))}
    </View>
  );
}
