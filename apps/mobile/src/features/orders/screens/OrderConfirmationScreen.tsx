import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketStackScreenProps } from '../../../navigation/types';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, Screen, queryState } from '../../../shared/ui';
import { useMarketContext } from '../../market/MarketContext';
import { useOrder } from '../api/hooks';
import { OrderSummary } from '../components/OrderSummary';
import { StatusTimeline } from '../components/StatusTimeline';
import { orderSlotLabel } from '../format';

/** Bestellbestätigung: check illustration, order number, slot, status timeline (simulated). */
export function OrderConfirmationScreen({ route, navigation }: MarketStackScreenProps<'OrderConfirmation'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { exit } = useMarketContext();
  const query = useOrder(route.params.orderId);
  const state = queryState(query);
  if (state !== null || query.data === undefined) {
    return <Screen safeTop>{state}</Screen>;
  }
  const order = query.data;
  return (
    <Screen
      safeTop
      padded={false}
      footer={
        <View style={styles.footer}>
          <Button title={t('order.backToMarket')} variant="secondary" onPress={() => navigation.navigate('MarketTabs', { screen: 'MarketHome' })} />
          <Button title={t('order.backToMarketplace')} onPress={exit} />
        </View>
      }>
      <ScrollView contentContainerStyle={styles.content} testID="order-confirmation">
        <View style={styles.check} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Icon name="checkCircle" size={72} color={colors.primary} strokeWidth={1.5} />
        </View>
        <AppText variant="largeTitle" center accessibilityRole="header" accessibilityLiveRegion="polite">
          {t('order.thanks')}
        </AppText>
        <AppText variant="body" color="textSecondary" center>
          {t('order.simulated')}
        </AppText>
        <View style={styles.card}>
          <AppText variant="subhead" color="textSecondary">
            {t('order.number')}
          </AppText>
          <AppText variant="headline" selectable>
            {order.orderNumber}
          </AppText>
          <AppText variant="subhead" color="textSecondary" style={styles.gapTop}>
            {order.market.name}
          </AppText>
          <AppText variant="body">{orderSlotLabel(t, order)}</AppText>
        </View>
        <StatusTimeline order={order} />
        <OrderSummary order={order} />
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, gap: t.spacing.lg, paddingBottom: t.spacing.xxxl },
  check: { alignItems: 'center', marginTop: t.spacing.xl },
  card: { backgroundColor: t.colors.surfaceMuted, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: 2 },
  gapTop: { marginTop: t.spacing.sm },
  footer: { gap: t.spacing.sm },
}));
