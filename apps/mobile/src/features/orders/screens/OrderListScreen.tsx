import { memo, useCallback } from 'react';
import { FlatList, StyleSheet, View, type ListRenderItem } from 'react-native';

import type { Order } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format/dates';
import { spacing } from '../../../shared/theme';
import { Badge, EmptyState, ListRow, Price, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useOrders } from '../api/hooks';
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE } from '../model';

const OrderRow = memo(function OrderRow({
  order,
  onOpen,
}: {
  order: Order;
  onOpen: (orderId: string) => void;
}) {
  return (
    <ListRow
      title={order.orderNumber}
      subtitle={`Placed ${formatDateTime(order.placedAt)}`}
      onPress={() => onOpen(order.id)}
      accessibilityHint="Shows order details"
      right={
        <View style={styles.right}>
          <Badge label={ORDER_STATUS_LABEL[order.status]} tone={ORDER_STATUS_TONE[order.status]} />
          <Price money={order.total} accessibilityPrefix="Total" />
        </View>
      }
    />
  );
});

export function OrderListScreen({ navigation }: RootStackScreenProps<'OrderList'>) {
  const orders = useOrders();
  const onOpen = useCallback(
    (orderId: string) => navigation.navigate('OrderDetail', { orderId }),
    [navigation],
  );
  const renderItem = useCallback<ListRenderItem<Order>>(
    ({ item }) => <OrderRow order={item} onOpen={onOpen} />,
    [onOpen],
  );

  const state = queryState(orders);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }
  return (
    <Screen padded={false}>
      <FlatList
        data={orders.data}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        refreshing={orders.isRefetching}
        onRefresh={() => void orders.refetch()}
        contentContainerStyle={styles.list}
        ListEmptyComponent={<EmptyState title="No orders yet" message="Your orders will appear here." />}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { flexGrow: 1 },
  right: { alignItems: 'flex-end', gap: spacing.xs },
});
