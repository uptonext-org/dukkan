import { Alert, ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { formatAddressLine } from '../../addresses/validation';
import { useCancelOrder, useOrder } from '../api/hooks';
import { OrderSummary } from '../components/OrderSummary';
import { StatusTimeline } from '../components/StatusTimeline';
import { orderSlotLabel } from '../format';
import { canCancel } from '../model';

export function OrderDetailScreen({ route, navigation }: RootStackScreenProps<'OrderDetail'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const query = useOrder(route.params.orderId);
  const cancel = useCancelOrder();
  const header = <ScreenHeader title={t('order.title')} onBack={() => navigation.goBack()} />;
  const state = queryState(query);
  if (state !== null || query.data === undefined) {
    return <Screen header={header}>{state}</Screen>;
  }
  const order = query.data;
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
        {canCancel(order) && <Button title={t('order.cancel')} variant="danger" onPress={confirmCancel} loading={cancel.isPending} />}
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, gap: t.spacing.xl, paddingBottom: t.spacing.xxxl },
  card: { backgroundColor: t.colors.surfaceMuted, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: 4 },
}));
