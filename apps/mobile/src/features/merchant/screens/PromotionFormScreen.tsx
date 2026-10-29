import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey } from '../../../shared/api';
import { formatMoney, isolate } from '../../../shared/format';
import { isLanguage, type Language } from '../../../shared/i18n';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Chip, Icon, IconButton, ListRow, Screen, ScreenHeader, SegmentedControl, TextField, ToggleSwitch } from '../../../shared/ui';
import { hasErrors } from '../../auth/validation';
import { toggle } from '../../marketplace/model';
import { useCreatePromotion, useListings, useMyMarkets } from '../api/hooks';
import { LocalizedFields } from '../components/LocalizedFields';
import { emptyPromotion, toPromotionRequest, validatePromotion, type Errors, type PromotionField, type PromotionValues } from '../validation';

const DURATIONS = [3, 7, 14, 30];
const STARTS = [0, 1, 2, 7];

/** Weekday names Monday..Sunday (ISO 1..7) in the UI language. */
function weekdayNames(language: string): string[] {
  const format = new Intl.DateTimeFormat(language === 'ar' ? 'ar-u-nu-latn' : language, { weekday: 'short', timeZone: 'UTC' });
  // 1 January 2024 was a Monday.
  return [0, 1, 2, 3, 4, 5, 6].map((i) => format.format(new Date(Date.UTC(2024, 0, 1 + i))));
}

/**
 * Create a promotion for selected listings: percent off, quantity prices (3 for 5 €, 7 for 10 €)
 * or an offer price per product; from today or later, for some days, optionally only on some
 * weekdays and hours.
 */
export function PromotionFormScreen({ route, navigation }: RootStackScreenProps<'MerchantPromotionForm'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t, i18n } = useTranslation();
  const { marketId } = route.params;
  const names = weekdayNames(i18n.language);
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
    const prices = Object.fromEntries(listings.listings.map((l) => [l.id, l.price.amountCents]));
    const found = validatePromotion(values, primaryLocale, new Date(), prices);
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
        <LocalizedFields label={t('merchant.promotionTitle')} values={values.title} primary={primaryLocale} error={err('title')} onChange={(texts) => set('title', texts)} testIDPrefix="promotion-title" translateKind="PROMOTION" />
        <SegmentedControl
          accessibilityLabel={t('merchant.promotionType')}
          segments={[
            { value: 'PERCENT_OFF', label: t('merchant.typePercent') },
            { value: 'MULTI_BUY', label: t('merchant.typeMultiBuy') },
            { value: 'FIXED_PRICE', label: t('merchant.typeFixed') },
          ]}
          value={values.type}
          onChange={(type) => set('type', type)}
        />
        <View style={styles.gap} />
        {values.type === 'PERCENT_OFF' && (
          <TextField label={t('merchant.percent')} value={values.percentOff} onChangeText={(v) => set('percentOff', v)} error={err('percentOff')} keyboardType="number-pad" inputDirection="ltr" />
        )}
        {values.type === 'MULTI_BUY' && (
          <View testID="promotion-tiers">
            <AppText variant="caption" color="textSecondary">
              {t('merchant.tiersHint')}
            </AppText>
            {values.tiers.map((tier, index) => (
              <View key={index} style={styles.tierRow}>
                <View style={styles.flex}>
                  <TextField
                    label={t('merchant.tierQuantity', { n: index + 1 })}
                    value={tier.quantity}
                    onChangeText={(v) => set('tiers', values.tiers.map((x, i) => (i === index ? { ...x, quantity: v } : x)))}
                    keyboardType="number-pad"
                    inputDirection="ltr"
                    testID={`tier-quantity-${index}`}
                  />
                </View>
                <View style={styles.flex}>
                  <TextField
                    label={t('merchant.tierPrice', { n: index + 1 })}
                    value={tier.price}
                    onChangeText={(v) => set('tiers', values.tiers.map((x, i) => (i === index ? { ...x, price: v } : x)))}
                    keyboardType="decimal-pad"
                    inputDirection="ltr"
                    testID={`tier-price-${index}`}
                  />
                </View>
                {values.tiers.length > 1 && (
                  <IconButton icon="trash" label={t('merchant.removeTier', { n: index + 1 })} onPress={() => set('tiers', values.tiers.filter((_, i) => i !== index))} />
                )}
              </View>
            ))}
            {err('tiers') !== undefined && (
              <AppText variant="caption" color="accent" accessibilityRole="alert">
                {err('tiers')}
              </AppText>
            )}
            {values.tiers.length < 5 && (
              <Button title={t('merchant.addTier')} variant="ghost" icon="plus" onPress={() => set('tiers', [...values.tiers, { quantity: '', price: '' }])} testID="tier-add" />
            )}
          </View>
        )}
        {values.type === 'FIXED_PRICE' && (
          <AppText variant="caption" color="textSecondary">
            {t('merchant.fixedHint')}
          </AppText>
        )}
        <AppText variant="subhead" color="textSecondary">
          {t('merchant.start')}
        </AppText>
        <View style={styles.wrap}>
          {STARTS.map((n) => (
            <Chip key={n} label={n === 0 ? t('merchant.startToday') : n === 1 ? t('merchant.startTomorrow') : t('merchant.startIn', { n })} selected={values.startInDays === n} onPress={() => set('startInDays', n)} />
          ))}
        </View>
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
        <AppText variant="subhead" color="textSecondary">
          {t('merchant.weekdays')}
        </AppText>
        <View style={styles.wrap} testID="promotion-weekdays">
          {names.map((name, i) => (
            <Chip key={name} label={name} selected={values.weekdays.includes(i + 1)} onPress={() => set('weekdays', values.weekdays.includes(i + 1) ? values.weekdays.filter((d) => d !== i + 1) : [...values.weekdays, i + 1])} testID={`weekday-${i + 1}`} />
          ))}
        </View>
        {err('weekdays') !== undefined && (
          <AppText variant="caption" color="accent" accessibilityRole="alert">
            {err('weekdays')}
          </AppText>
        )}
        <View style={styles.switchRow}>
          <AppText variant="body" style={styles.flex}>
            {t('merchant.allDay')}
          </AppText>
          <ToggleSwitch value={values.allDay} onValueChange={(v) => set('allDay', v)} accessibilityLabel={t('merchant.allDay')} testID="promotion-all-day" />
        </View>
        {!values.allDay && (
          <View style={styles.tierRow}>
            <View style={styles.flex}>
              <TextField label={t('merchant.dailyFrom')} value={values.dailyFrom} onChangeText={(v) => set('dailyFrom', v)} inputDirection="ltr" placeholder="08:00" testID="daily-from" />
            </View>
            <View style={styles.flex}>
              <TextField label={t('merchant.dailyUntil')} value={values.dailyUntil} onChangeText={(v) => set('dailyUntil', v)} inputDirection="ltr" placeholder="12:00" testID="daily-until" />
            </View>
          </View>
        )}
        {err('daily') !== undefined && (
          <AppText variant="caption" color="accent" accessibilityRole="alert">
            {err('daily')}
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
        {err('offers') !== undefined && (
          <AppText variant="caption" color="accent" accessibilityRole="alert">
            {err('offers')}
          </AppText>
        )}
        {listings.listings.map((listing) => {
          const selected = values.productIds.includes(listing.id);
          return (
            <View key={listing.id}>
              <ListRow
                title={listing.name}
                subtitle={`${listing.unitSize} · ${isolate(formatMoney(listing.price))}`}
                chevron={false}
                selected={selected}
                onPress={() => set('productIds', toggle(values.productIds, listing.id))}
                trailing={selected ? <Icon name="check" color={colors.primary} /> : undefined}
              />
              {selected && values.type === 'FIXED_PRICE' && (
                <TextField
                  label={t('merchant.offerPriceFor', { name: listing.name })}
                  value={values.offers[listing.id] ?? ''}
                  onChangeText={(v) => set('offers', { ...values.offers, [listing.id]: v })}
                  keyboardType="decimal-pad"
                  inputDirection="ltr"
                  testID={`offer-${listing.id}`}
                />
              )}
            </View>
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
  flex: { flex: 1 },
  tierRow: { flexDirection: 'row', alignItems: 'flex-end', gap: t.spacing.sm },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: t.spacing.md },
}));
