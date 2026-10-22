import { useState } from 'react';
import { Alert, ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { openMarketCart } from '../../../navigation/marketLinks';
import type { RootStackScreenProps } from '../../../navigation/types';
import type { ReorderResult } from '../../../shared/api';
import { errorMessageKey } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { formatAddressLine } from '../../addresses/validation';
import { useCancelOrder, useOrder, useReorder } from '../api/hooks';
import { OrderSummary } from '../components/OrderSummary';
import { StatusTimeline } from '../components/StatusTimeline';
import { orderSlotLabel } from '../format';
import { canCancel } from '../model';

export function OrderDetailScreen({ route, navigation }: RootStackScreenProps<'OrderDetail'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const query = useOrder(route.params.orderId);
  const cancel = useCancelOrder();
  const reorder = useReorder();
  // What could not go into the cart; shown until the user leaves.
  const [reordered, setReordered] = useState<ReorderResult | null>(null);
  const header = <ScreenHeader title={t('order.title')} onBack={() => navigation.goBack()} />;
  const state = queryState(query);
  if (state !== null || query.data === undefined) {
    return <Screen header={header}>{state}</Screen>;
  }
  const order = query.data;
  const orderAgain = () =>
    reorder.mutate(order.id, {
      onSuccess: (result) => {
        if (result.skipped.length === 0) {
          openMarketCart(navigation, result.marketId);
        } else {
          setReordered(result);
        }
      },
    });
  const confirmCancel = () =>
    Alert.alert(t('order.cancelTitle'), t('order.cancelMessage'), [
      { text: t('common.no'), style: 'cancel' },
      { text: t('order.cancel'), style: 'destructive', onPress: () => cancel.mutate(order.id) },
    ]);
  return (
    <Screen padded={false} header={header}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <AppText variant="headline">{order.market.name}</AppText>
          <AppText variant="subhead" color="textSecondary" selectable>
            {`${order.orderNumber} · ${formatDateTime(order.placedAt)}`}
          </AppText>
          <AppText variant="body">{orderSlotLabel(t, order)}</AppText>
          {order.deliveryAddress != null && <AppText variant="body">{formatAddressLine(order.deliveryAddress)}</AppText>}
        </View>
        <StatusTimeline order={order} />
        <OrderSummary order={order} />
        {cancel.isError && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert">
            {t(errorMessageKey(cancel.error))}
          </AppText>
        )}
        {reordered !== null && (
          <View style={styles.notice} accessibilityLiveRegion="polite" testID="reorder-notice">
            <AppText variant="bodyStrong">{reordered.addedProducts > 0 ? t('order.reorderSkippedTitle') : t('order.reorderNothing')}</AppText>
            {reordered.skipped.map((s) => (
              <AppText key={s.productId} variant="subhead" color="textSecondary">
                {t(`order.skipped.${s.reason}`, { name: s.name })}
              </AppText>
            ))}
            {(reordered.addedProducts > 0 || reordered.skipped.some((s) => s.reason === 'LIMITED')) && (
              <Button title={t('order.toCart')} variant="secondary" onPress={() => openMarketCart(navigation, reordered.marketId)} testID="reorder-to-cart" />
            )}
          </View>
        )}
        {reorder.isError && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert">
            {t(errorMessageKey(reorder.error))}
          </AppText>
        )}
        {reordered === null && (
          <Button title={t('order.reorder')} icon="basket" onPress={orderAgain} loading={reorder.isPending} testID="reorder" />
        )}
        {canCancel(order) && <Button title={t('order.cancel')} variant="danger" onPress={confirmCancel} loading={cancel.isPending} />}
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, gap: t.spacing.xl, paddingBottom: t.spacing.xxxl },
  card: { backgroundColor: t.colors.surfaceMuted, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: 4 },
  notice: { borderWidth: 1, borderColor: t.colors.separator, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: t.spacing.sm },
}));
