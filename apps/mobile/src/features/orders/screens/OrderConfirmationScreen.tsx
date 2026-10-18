import { StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '../../../shared/theme';
import { Button, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useOrder } from '../api/hooks';
import { OrderSummary } from '../components/OrderSummary';

export function OrderConfirmationScreen({ navigation, route }: RootStackScreenProps<'OrderConfirmation'>) {
  const order = useOrder(route.params.orderId);
  const state = queryState(order);
  if (state) {
    return <Screen>{state}</Screen>;
  }
  if (order.data === undefined) {
    return null;
  }
  return (
    <Screen scroll>
      <View style={styles.header} accessible accessibilityRole="header" accessibilityLiveRegion="polite">
        <Text style={styles.title}>Thank you for your order</Text>
        <Text style={styles.number}>Order number {order.data.orderNumber}</Text>
      </View>
      <OrderSummary order={order.data} />
      <Button
        title="View order"
        variant="secondary"
        style={styles.button}
        onPress={() => navigation.replace('OrderDetail', { orderId: order.data.id })}
      />
      <Button
        title="Continue shopping"
        style={styles.button}
        onPress={() => navigation.popTo('Tabs', { screen: 'Home' })}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { marginBottom: spacing.lg },
  title: { ...typography.title, color: colors.primary },
  number: { ...typography.body, color: colors.textMuted, marginTop: spacing.xs },
  button: { marginTop: spacing.md },
});
