import { ScrollView } from 'react-native';
import { useTranslation } from 'react-i18next';

import { openMarket } from '../../../navigation/marketLinks';
import type { RootStackScreenProps } from '../../../navigation/types';
import { makeStyles } from '../../../shared/theme';
import { AppText, ListRow, Screen, ScreenHeader } from '../../../shared/ui';
import { useMyMarkets } from '../api/hooks';
import { OrderingControls } from '../components/OrderingControls';

export function MarketHubScreen({ route, navigation }: RootStackScreenProps<'MerchantMarketHub'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = route.params;
  const market = useMyMarkets().data?.find((m) => m.id === marketId);
  return (
    <Screen padded={false} header={<ScreenHeader title={market?.name ?? t('merchant.myMarket')} onBack={() => navigation.goBack()} />}>
      <ScrollView contentContainerStyle={styles.content}>
        {market !== undefined && (
          <AppText variant="subhead" color="textSecondary" style={styles.status}>
            {`${t(`merchant.status.${market.status}`)} · ${market.addressLine}`}
          </AppText>
        )}
        <OrderingControls marketId={marketId} />
        <ListRow title={t('merchant.profile')} icon="edit" onPress={() => navigation.navigate('MerchantMarketForm', { marketId })} />
        <ListRow title={t('merchant.listings')} icon="package" onPress={() => navigation.navigate('MerchantListings', { marketId })} testID="hub-listings" />
        <ListRow title={t('merchant.addProduct')} icon="plus" onPress={() => navigation.navigate('MerchantAddProduct', { marketId })} />
        <ListRow title={t('merchant.promotions')} icon="percent" onPress={() => navigation.navigate('MerchantPromotions', { marketId })} />
        <ListRow title={t('merchant.orders')} icon="orders" onPress={() => navigation.navigate('MerchantOrders', { marketId })} />
        <ListRow title={t('merchantFeedback.entry')} icon="message" onPress={() => navigation.navigate('MerchantFeedback', { marketId })} testID="hub-feedback" />
        <ListRow title={t('merchant.viewAsCustomer')} icon="store" onPress={() => openMarket(navigation, marketId)} />
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl },
  status: { marginBottom: t.spacing.md },
}));
