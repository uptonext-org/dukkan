import { useState } from 'react';
import { FlatList, RefreshControl, ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type Order, type OrderStatus } from '../../../shared/api';
import { formatMoney } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Chip, EmptyState, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { formatAddressLine } from '../../addresses/validation';
import { orderSlotLabel } from '../../orders/format';
import { nextMerchantStatuses } from '../../orders/model';
import { useAdvanceOrder, useMarketOrders } from '../api/hooks';

const FILTERS: (OrderStatus | undefined)[] = [undefined, 'PLACED', 'ACCEPTED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'COMPLETED'];

function OrderCard({ order }: { order: Order }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const advance = useAdvanceOrder();
  const next = nextMerchantStatuses(order);
  return (
    <View style={styles.card} testID={`merchant-order-${order.id}`}>
      <View style={styles.header}>
        <AppText variant="headline" style={styles.flex}>
          {order.customerName ?? order.orderNumber}
        </AppText>
        <AppText variant="headline" dense>
          {formatMoney(order.total)}
        </AppText>
      </View>
      <AppText variant="subhead" color="textSecondary" selectable>
        {`${order.orderNumber} · ${t(`orderStatus.${order.status}`)}`}
      </AppText>
      <AppText variant="body">{orderSlotLabel(t, order)}</AppText>
      {order.deliveryAddress != null && <AppText variant="subhead">{formatAddressLine(order.deliveryAddress)}</AppText>}
      <AppText variant="subhead" color="textSecondary">
        {order.items.map((i) => `${i.quantity} × ${i.name}`).join(', ')}
      </AppText>
      {next.length > 0 && (
        <View style={styles.actions}>
          {next.map((status) => (
            <Button
              key={status}
              title={t(`merchant.action.${status}`)}
              variant={status === 'REJECTED' ? 'danger' : 'primary'}
              onPress={() => advance.mutate({ orderId: order.id, status })}
              loading={advance.isPending && advance.variables.status === status}
              style={styles.flex}
              testID={`advance-${order.id}-${status}`}
            />
          ))}
        </View>
      )}
      {advance.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(advance.error))}
        </AppText>
      )}
    </View>
  );
}

/** Incoming orders with status buttons following the contract transitions. */
export function MerchantOrdersScreen({ route, navigation }: RootStackScreenProps<'MerchantOrders'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const [status, setStatus] = useState<OrderStatus | undefined>(undefined);
  const query = useMarketOrders(route.params.marketId, status);
  return (
    <Screen padded={false} header={<ScreenHeader title={t('merchant.orders')} onBack={() => navigation.goBack()} />}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsWrap} contentContainerStyle={styles.chips}>
        {FILTERS.map((s) => (
          <Chip key={s ?? 'all'} label={s === undefined ? t('discover.all') : t(`orderStatus.${s}`)} selected={status === s} onPress={() => setStatus(s)} />
        ))}
      </ScrollView>
      {queryState(query) ?? (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(o) => o.id}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={query.isRefetching} onRefresh={() => void query.refetch()} tintColor={colors.primary} />}
          ListEmptyComponent={<EmptyState icon="orders" title={t('merchant.noOrdersTitle')} message={t('merchant.noOrdersMessage')} />}
          renderItem={({ item }) => <OrderCard order={item} />}
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  chipsWrap: { flexGrow: 0, marginBottom: t.spacing.sm },
  chips: { gap: t.spacing.sm, paddingHorizontal: t.layout.gutter },
  list: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl, gap: t.spacing.md, flexGrow: 1 },
  card: { backgroundColor: t.colors.surfaceMuted, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: 4 },
  header: { flexDirection: 'row', alignItems: 'baseline', gap: t.spacing.md },
  flex: { flex: 1 },
  actions: { flexDirection: 'row', gap: t.spacing.sm, marginTop: t.spacing.sm },
}));
