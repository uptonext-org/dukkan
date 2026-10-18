import { Alert, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format/dates';
import { colors, spacing, typography } from '../../../shared/theme';
import { Badge, Button, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useCancelOrder, useOrder } from '../api/hooks';
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE, canCancel } from '../model';
import { OrderSummary } from '../components/OrderSummary';

export function OrderDetailScreen({ route }: RootStackScreenProps<'OrderDetail'>) {
  const order = useOrder(route.params.orderId);
  const cancel = useCancelOrder();

  const state = queryState(order);
  if (state) {
    return <Screen>{state}</Screen>;
  }
  const o = order.data;
  if (o === undefined) {
    return null;
  }

  const confirmCancel = () =>
    Alert.alert('Cancel this order?', `Order ${o.orderNumber} will be cancelled.`, [
      { text: 'Keep order', style: 'cancel' },
      { text: 'Cancel order', style: 'destructive', onPress: () => cancel.mutate(o.id) },
    ]);

  return (
    <Screen scroll>
      <View style={styles.header}>
        <Text style={styles.title} accessibilityRole="header">
          {o.orderNumber}
        </Text>
        <Badge
          label={ORDER_STATUS_LABEL[o.status]}
          tone={ORDER_STATUS_TONE[o.status]}
          accessibilityLabel={`Status: ${ORDER_STATUS_LABEL[o.status]}`}
        />
      </View>
      <Text style={styles.meta}>Placed {formatDateTime(o.placedAt)}</Text>
      <OrderSummary order={o} />
      {cancel.isError && (
        <Text style={styles.error} accessibilityRole="alert">
          {errorMessage(cancel.error)}
        </Text>
      )}
      {canCancel(o) && (
        <>
          <Text style={styles.meta}>You can cancel until {formatDateTime(o.slot.orderCutoffAt)}.</Text>
          <Button
            title="Cancel order"
            variant="danger"
            onPress={confirmCancel}
            loading={cancel.isPending}
            style={styles.button}
          />
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md },
  title: { ...typography.heading, color: colors.text, flex: 1 },
  meta: { ...typography.caption, color: colors.textMuted, marginVertical: spacing.sm },
  error: { ...typography.body, color: colors.danger, marginTop: spacing.md },
  button: { marginTop: spacing.sm },
});
