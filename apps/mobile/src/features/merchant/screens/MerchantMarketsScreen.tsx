import { FlatList } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { makeStyles } from '../../../shared/theme';
import { Button, EmptyState, ImageTile, ListRow, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { useMyMarkets } from '../api/hooks';

/** Mein Markt: the merchant's markets. */
export function MerchantMarketsScreen({ navigation }: RootStackScreenProps<'MerchantMarkets'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const query = useMyMarkets();
  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={t('merchant.myMarkets')} onBack={() => navigation.goBack()} />}
      footer={<Button title={t('merchant.createMarket')} icon="plus" onPress={() => navigation.navigate('MerchantMarketForm')} />}>
      {queryState(query) ?? (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(m) => m.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<EmptyState icon="store" title={t('merchant.noMarketsTitle')} message={t('merchant.noMarketsMessage')} />}
          renderItem={({ item }) => (
            <ListRow
              title={item.name}
              subtitle={`${item.type.name} · ${t(`merchant.status.${item.status}`)}`}
              leading={<ImageTile uri={item.logoUrl} size={48} radius={24} tint="sand" placeholderIcon="store" />}
              onPress={() => navigation.navigate('MerchantMarketHub', { marketId: item.id })}
            />
          )}
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  list: { paddingHorizontal: t.layout.gutter, flexGrow: 1 },
}));
