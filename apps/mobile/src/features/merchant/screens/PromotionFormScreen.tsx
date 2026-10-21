import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey } from '../../../shared/api';
import { formatMoney, isolate } from '../../../shared/format';
import { isLanguage, type Language } from '../../../shared/i18n';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Chip, Icon, ListRow, Screen, ScreenHeader, SegmentedControl, TextField } from '../../../shared/ui';
import { hasErrors } from '../../auth/validation';
import { toggle } from '../../marketplace/model';
import { useCreatePromotion, useListings, useMyMarkets } from '../api/hooks';
import { LocalizedFields } from '../components/LocalizedFields';
import { emptyPromotion, toPromotionRequest, validatePromotion, type Errors, type PromotionField, type PromotionValues } from '../validation';

const DURATIONS = [3, 7, 14, 30];

/** Create a percent-off or multi-buy promotion for selected listings. */
export function PromotionFormScreen({ route, navigation }: RootStackScreenProps<'MerchantPromotionForm'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { marketId } = route.params;
  const market = useMyMarkets().data?.find((m) => m.id === marketId);
  const primaryLocale: Language = isLanguage(market?.primaryLocale) ? market.primaryLocale : 'de';
  const listings = useListings(marketId, '');
  const create = useCreatePromotion(marketId);
  const [values, setValues] = useState<PromotionValues>(emptyPromotion);
  const [errors, setErrors] = useState<Errors<PromotionField>>({});
  const set = <K extends keyof PromotionValues>(key: K, value: PromotionValues[K]) => setValues((v) => ({ ...v, [key]: value }));
  const err = (field: PromotionField) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };
  const submit = () => {
    const found = validatePromotion(values, primaryLocale);
    setErrors(found);
    if (hasErrors(found)) {
      return;
    }
    create.mutate(toPromotionRequest(values), { onSuccess: () => navigation.goBack() });
  };
  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={t('merchant.createPromotion')} onBack={() => navigation.goBack()} />}
      footer={
        <View>
          {create.isError && (
            <AppText variant="subhead" color="accent" accessibilityRole="alert">
              {t(errorMessageKey(create.error))}
            </AppText>
          )}
          <Button title={t('common.save')} onPress={submit} loading={create.isPending} />
        </View>
      }>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <LocalizedFields label={t('merchant.promotionTitle')} values={values.title} primary={primaryLocale} error={err('title')} onChange={(lang, text) => set('title', { ...values.title, [lang]: text })} testIDPrefix="promotion-title" translateKind="PROMOTION" />
        <SegmentedControl
          accessibilityLabel={t('merchant.promotionType')}
          segments={[
            { value: 'PERCENT_OFF', label: t('merchant.typePercent') },
            { value: 'MULTI_BUY', label: t('merchant.typeMultiBuy') },
          ]}
          value={values.type}
          onChange={(type) => set('type', type)}
        />
        <View style={styles.gap} />
        {values.type === 'PERCENT_OFF' ? (
          <TextField label={t('merchant.percent')} value={values.percentOff} onChangeText={(v) => set('percentOff', v)} error={err('percentOff')} keyboardType="number-pad" inputDirection="ltr" />
        ) : (
          <>
            <TextField label={t('merchant.bundleQuantity')} value={values.bundleQuantity} onChangeText={(v) => set('bundleQuantity', v)} error={err('bundleQuantity')} keyboardType="number-pad" inputDirection="ltr" />
            <TextField label={t('merchant.bundlePrice')} value={values.bundlePrice} onChangeText={(v) => set('bundlePrice', v)} error={err('bundlePrice')} keyboardType="decimal-pad" inputDirection="ltr" />
          </>
        )}
        <AppText variant="subhead" color="textSecondary">
          {t('merchant.duration')}
        </AppText>
        <View style={styles.wrap}>
          {DURATIONS.map((days) => (
            <Chip key={days} label={t('merchant.days', { count: days })} selected={values.days === days} onPress={() => set('days', days)} />
          ))}
        </View>
        {err('days') !== undefined && (
          <AppText variant="caption" color="accent" accessibilityRole="alert">
            {err('days')}
          </AppText>
        )}
        <AppText variant="headline" accessibilityRole="header" style={styles.gapTop}>
          {t('merchant.products')}
        </AppText>
        {err('productIds') !== undefined && (
          <AppText variant="caption" color="accent" accessibilityRole="alert">
            {err('productIds')}
          </AppText>
        )}
        {listings.listings.map((listing) => {
          const selected = values.productIds.includes(listing.id);
          return (
            <ListRow
              key={listing.id}
              title={listing.name}
              subtitle={`${listing.unitSize} · ${isolate(formatMoney(listing.price))}`}
              chevron={false}
              selected={selected}
              onPress={() => set('productIds', toggle(values.productIds, listing.id))}
              trailing={selected ? <Icon name="check" color={colors.primary} /> : undefined}
            />
          );
        })}
        {listings.hasNextPage && <Button title={t('common.loadMore')} variant="ghost" onPress={() => void listings.fetchNextPage()} />}
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl, gap: t.spacing.xs },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: t.spacing.sm },
  gap: { height: t.spacing.md },
  gapTop: { marginTop: t.spacing.lg },
}));
