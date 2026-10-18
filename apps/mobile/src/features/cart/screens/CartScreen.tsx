import { useCallback } from 'react';
import { FlatList, StyleSheet, Text, View, type ListRenderItem } from 'react-native';

import { errorMessage, type CartItem } from '../../../shared/api';
import { colors, spacing, typography } from '../../../shared/theme';
import { Button, EmptyState, Screen, queryState } from '../../../shared/ui';
import type { TabScreenProps } from '../../../navigation/types';
import { useCart, useCartUpdater, usePlaceOrder } from '../api/hooks';
import { CartLine } from '../components/CartLine';
import { CartTotals, DeliveryCard, MinimumOrderNotice } from '../components/CartSummary';

const keyExtractor = (item: CartItem) => item.productId;

export function CartScreen({ navigation }: TabScreenProps<'Cart'>) {
  const cart = useCart();
  const { onChangeQuantity, pendingProductId, error: updateError } = useCartUpdater();
  const placeOrder = usePlaceOrder();

  const renderItem = useCallback<ListRenderItem<CartItem>>(
    ({ item }) => (
      <CartLine item={item} busy={pendingProductId === item.productId} onChangeQuantity={onChangeQuantity} />
    ),
    [pendingProductId, onChangeQuantity],
  );

  const state = queryState(cart);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }
  const data = cart.data;
  if (data === undefined) {
    return null;
  }
  if (data.items.length === 0) {
    return (
      <Screen>
        <EmptyState
          title="Your cart is empty"
          message="Add products from the catalog to get started."
          actionLabel="Browse products"
          onAction={() => navigation.navigate('Home')}
        />
      </Screen>
    );
  }

  const canCheckout = data.meetsMinimumOrderValue && data.delivery != null;
  const checkout = () =>
    placeOrder.mutate(undefined, {
      onSuccess: (order) => navigation.navigate('OrderConfirmation', { orderId: order.id }),
    });

  return (
    <Screen
      padded={false}
      footer={
        <View>
          {placeOrder.isError && (
            <Text style={styles.error} accessibilityRole="alert">
              {errorMessage(placeOrder.error)}
            </Text>
          )}
          {!canCheckout && (
            <Text style={styles.hint}>
              {data.meetsMinimumOrderValue
                ? 'Choose a delivery slot to check out.'
                : 'Reach the minimum order value to check out.'}
            </Text>
          )}
          <Button
            title="Place order"
            onPress={checkout}
            disabled={!canCheckout}
            loading={placeOrder.isPending}
            accessibilityHint="Places your order. Payment is simulated, nothing is charged."
          />
        </View>
      }>
      <FlatList
        data={data.items}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        refreshing={cart.isRefetching}
        onRefresh={() => void cart.refetch()}
        ListHeaderComponent={
          <View>
            <Text style={styles.count} accessibilityRole="header">
              {data.itemCount === 1 ? '1 item' : `${data.itemCount} items`}
            </Text>
            {updateError !== null && (
              <Text style={[styles.error, styles.inset]} accessibilityRole="alert">
                {errorMessage(updateError)}
              </Text>
            )}
          </View>
        }
        ListFooterComponent={
          <View style={styles.footerContent}>
            <MinimumOrderNotice cart={data} />
            <DeliveryCard
              cart={data}
              onChoose={() => navigation.navigate('AddressList', { selectForDelivery: true })}
            />
            <CartTotals cart={data} />
          </View>
        }
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  count: { ...typography.heading, color: colors.text, padding: spacing.lg },
  error: { ...typography.caption, color: colors.danger, marginBottom: spacing.sm },
  inset: { paddingHorizontal: spacing.lg },
  hint: { ...typography.caption, color: colors.textMuted, marginBottom: spacing.sm, textAlign: 'center' },
  footerContent: { paddingBottom: spacing.xl },
});
