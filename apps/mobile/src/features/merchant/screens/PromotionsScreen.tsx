import type { TFunction } from 'i18next';
import { useState } from 'react';
import { Alert, FlatList, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type Promotion } from '../../../shared/api';
import { formatDate, formatMoney, formatPercent } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, EmptyState, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { useEndPromotion, useMyPromotions } from '../api/hooks';

export function promotionMechanic(t: TFunction, promotion: Promotion): string {
  if (promotion.type === 'PERCENT_OFF' && promotion.percentOff != null) {
    return t('merchant.percentOff', { percent: formatPercent(promotion.percentOff) });
  }
  if (promotion.bundleQuantity != null && promotion.bundlePrice != null) {
    return t('merchant.multiBuy', { quantity: String(promotion.bundleQuantity), price: formatMoney(promotion.bundlePrice) });
  }
  return '';
}

/** Promotions of my market: active and expired; "end now" for active ones. */
export function PromotionsScreen({ route, navigation }: RootStackScreenProps<'MerchantPromotions'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = route.params;
  const query = useMyPromotions(marketId);
  const end = useEndPromotion(marketId);
  const [now] = useState(() => Date.now());
  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={t('merchant.promotions')} onBack={() => navigation.goBack()} />}
      footer={<Button title={t('merchant.createPromotion')} icon="plus" onPress={() => navigation.navigate('MerchantPromotionForm', { marketId })} />}>
      {end.isError && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.error}>
          {t(errorMessageKey(end.error))}
        </AppText>
      )}
      {queryState(query) ?? (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(p) => p.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<EmptyState icon="percent" title={t('merchant.noPromotionsTitle')} message={t('merchant.noPromotionsMessage')} />}
          renderItem={({ item }) => {
            const active = Date.parse(item.validFrom) <= now && Date.parse(item.validUntil) > now;
            return (
              <View style={styles.row}>
                <View style={styles.flex}>
                  <AppText variant="bodyStrong">{item.title}</AppText>
                  <AppText variant="subhead" color="textSecondary">
                    {[promotionMechanic(t, item), t('merchant.productCount', { count: item.productIds.length }), `${formatDate(item.validFrom)}–${formatDate(item.validUntil)}`].filter(Boolean).join(' · ')}
                  </AppText>
                  <AppText variant="caption" color={active ? 'primary' : 'textSecondary'}>
                    {active ? t('merchant.active') : t('merchant.inactive')}
                  </AppText>
                </View>
                {active && (
                  <Button
                    title={t('merchant.endNow')}
                    variant="danger"
                    onPress={() =>
                      Alert.alert(t('merchant.endTitle'), item.title, [
                        { text: t('common.cancel'), style: 'cancel' },
                        { text: t('merchant.endNow'), style: 'destructive', onPress: () => end.mutate(item.id) },
                      ])
                    }
                  />
                )}
              </View>
            );
          }}
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  list: { paddingHorizontal: t.layout.gutter, flexGrow: 1 },
  row: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, paddingVertical: t.spacing.md, borderBottomWidth: 1, borderBottomColor: t.colors.separator },
  flex: { flex: 1, gap: 2 },
  error: { paddingHorizontal: t.layout.gutter },
}));
