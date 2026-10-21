import { FlatList, RefreshControl, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { formatDate, formatMoney } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, EmptyState, ListRow, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { useOrders } from '../api/hooks';

export function OrderListScreen({ navigation }: RootStackScreenProps<'Orders'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const query = useOrders();
  return (
    <Screen padded={false} header={<ScreenHeader title={t('profile.orders')} onBack={() => navigation.goBack()} />}>
      {queryState(query) ?? (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(o) => o.id}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={query.isRefetching} onRefresh={() => void query.refetch()} tintColor={colors.primary} />}
          ListEmptyComponent={<EmptyState icon="receipt" title={t('order.emptyTitle')} message={t('order.emptyMessage')} />}
          renderItem={({ item }) => (
            <ListRow
              title={item.market.name}
              subtitle={`${formatDate(item.placedAt)} · ${t(`orderStatus.${item.status}`)}`}
              trailing={
                <View>
                  <AppText variant="bodyStrong" dense>
                    {formatMoney(item.total)}
                  </AppText>
                </View>
              }
              onPress={() => navigation.navigate('OrderDetail', { orderId: item.id })}
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
