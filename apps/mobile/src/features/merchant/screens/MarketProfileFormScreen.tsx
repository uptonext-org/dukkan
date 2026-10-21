import { useState, type ReactNode } from 'react';
import { ScrollView, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../../../shared/format';
import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type MarketTag, type Place } from '../../../shared/api';
import { LANGUAGES, useLanguage, type Language } from '../../../shared/i18n';
import { PlaceSearch } from '../../../shared/location';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Chip, IconButton, queryState, Screen, ScreenHeader, TextField, ToggleSwitch } from '../../../shared/ui';
import { hasErrors } from '../../auth/validation';
import { useCuisines, useMarketTypes } from '../../marketplace/api/hooks';
import { toggle } from '../../marketplace/model';
import { useMyMarkets, useSaveMarket } from '../api/hooks';
import { LocalizedFields } from '../components/LocalizedFields';
import { PhotoField } from '../components/PhotoField';
import {
  emptyMarketProfile,
  endsNextDay,
  fromMerchantMarket,
  MAX_INTERVALS_PER_DAY,
  toMarketProfileRequest,
  validateMarketProfile,
  type DayHours,
  type Errors,
  type MarketProfileField,
  type MarketProfileValues,
  type TimeRange,
} from '../validation';

const TAGS: MarketTag[] = ['ORGANIC', 'REGIONAL', 'HALAL', 'VEGAN_FRIENDLY'];

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={{ marginTop: 24, gap: 8 }}>
      <AppText variant="headline" accessibilityRole="header">
        {title}
      </AppText>
      {children}
    </View>
  );
}

function ChipWrap({ children, label }: { children: ReactNode; label: string }) {
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }} accessibilityLabel={label}>
      {children}
    </View>
  );
}

/** Create or edit the market profile (design-system.md §4.4). */
export function MarketProfileFormScreen(props: RootStackScreenProps<'MerchantMarketForm'>) {
  const { language } = useLanguage();
  const marketId = props.route.params?.marketId;
  const markets = useMyMarkets();
  if (marketId !== undefined && markets.isPending) {
    return <Screen>{queryState(markets)}</Screen>;
  }
  const existing = markets.data?.find((m) => m.id === marketId);
  return <MarketProfileForm {...props} initial={existing !== undefined ? fromMerchantMarket(existing, language) : emptyMarketProfile(language)} />;
}

function MarketProfileForm({ route, navigation, initial }: RootStackScreenProps<'MerchantMarketForm'> & { initial: MarketProfileValues }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const marketId = route.params?.marketId;
  const types = useMarketTypes();
  const cuisines = useCuisines();
  const save = useSaveMarket(marketId);
  const [values, setValues] = useState<MarketProfileValues>(initial);
  const [errors, setErrors] = useState<Errors<MarketProfileField>>({});

  const set = <K extends keyof MarketProfileValues>(key: K, value: MarketProfileValues[K]) => {
    setValues((v) => ({ ...v, [key]: value }));
  };
  const err = (field: MarketProfileField) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };
  const text = (field: 'name' | 'phone' | 'legalName' | 'registerInfo' | 'deliveryRadiusKm' | 'deliveryFee' | 'freeDeliveryFrom' | 'minimumOrderValue') => ({
    value: values[field],
    onChangeText: (value: string) => set(field, value),
    error: err(field),
  });

  const fromPlace = (place: Place) => setValues((v) => ({ ...v, addressLine: place.label, location: place.location }));

  const submit = () => {
    const found = validateMarketProfile(values);
    setErrors(found);
    if (hasErrors(found)) {
      return;
    }
    save.mutate(toMarketProfileRequest(values), {
      onSuccess: (market) => {
        if (marketId === undefined) {
          navigation.replace('MerchantMarketHub', { marketId: market.id });
        } else {
          navigation.goBack();
        }
      },
    });
  };

  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={marketId !== undefined ? t('merchant.editMarket') : t('merchant.createMarket')} onBack={() => navigation.goBack()} />}
      footer={
        <View>
          {(hasErrors(errors) || save.isError) && (
            <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.formError}>
              {save.isError ? t(errorMessageKey(save.error)) : t('validation.checkFields')}
            </AppText>
          )}
          <Button title={t('common.save')} onPress={submit} loading={save.isPending} testID="market-save" />
        </View>
      }>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <TextField label={t('merchant.marketName')} {...text('name')} />

        <Section title={t('merchant.marketType')}>
          <ChipWrap label={t('merchant.marketType')}>
            {(types.data ?? []).map((type) => (
              <Chip key={type.code} label={type.name} selected={values.typeCode === type.code} onPress={() => set('typeCode', type.code)} />
            ))}
          </ChipWrap>
          {err('typeCode') !== undefined && <AppText variant="caption" color="accent">{err('typeCode')}</AppText>}
        </Section>

        <Section title={t('merchant.cuisines')}>
          <ChipWrap label={t('merchant.cuisines')}>
            {(cuisines.data ?? []).map((c) => (
              <Chip key={c.code} label={c.name} selected={values.cuisineCodes.includes(c.code)} onPress={() => set('cuisineCodes', toggle(values.cuisineCodes, c.code))} />
            ))}
          </ChipWrap>
          {err('cuisineCodes') !== undefined && <AppText variant="caption" color="accent">{err('cuisineCodes')}</AppText>}
        </Section>

        <Section title={t('merchant.primaryLanguage')}>
          <ChipWrap label={t('merchant.primaryLanguage')}>
            {LANGUAGES.map((l) => (
              <Chip key={l.code} label={l.nativeName} selected={values.primaryLocale === l.code} onPress={() => set('primaryLocale', l.code)} />
            ))}
          </ChipWrap>
          <LocalizedFields
            label={t('merchant.description')}
            values={values.description}
            primary={values.primaryLocale}
            multiline
            onChange={(lang: Language, value) => set('description', { ...values.description, [lang]: value })}
          />
        </Section>

        <Section title={t('merchant.address')}>
          <PlaceSearch onSelect={fromPlace} placeholder={t('merchant.addressSearch')} />
          <AppText variant="body" color={values.location !== null ? 'text' : 'textSecondary'}>
            {values.location !== null ? values.addressLine : t('merchant.addressHint')}
          </AppText>
          {err('addressLine') !== undefined && <AppText variant="caption" color="accent">{err('addressLine')}</AppText>}
          <TextField label={t('merchant.phone')} {...text('phone')} keyboardType="phone-pad" />
        </Section>

        <Section title={t('merchant.fulfilment')}>
          {(['delivery', 'pickup'] as const).map((key) => (
            <View key={key} style={styles.switchRow}>
              <AppText variant="body" style={styles.flex}>
                {t(`market.${key}`)}
              </AppText>
              <ToggleSwitch value={values[key]} onValueChange={(v) => set(key, v)} accessibilityLabel={t(`market.${key}`)} />
            </View>
          ))}
          {err('fulfilment') !== undefined && <AppText variant="caption" color="accent">{err('fulfilment')}</AppText>}
          {values.delivery && (
            <>
              <TextField label={t('merchant.deliveryRadius')} {...text('deliveryRadiusKm')} keyboardType="decimal-pad" />
              <TextField label={t('merchant.deliveryFee')} {...text('deliveryFee')} keyboardType="decimal-pad" />
              <TextField label={t('merchant.freeDeliveryFrom')} {...text('freeDeliveryFrom')} keyboardType="decimal-pad" hint={t('merchant.optional')} />
            </>
          )}
          <TextField label={t('merchant.minimumOrder')} {...text('minimumOrderValue')} keyboardType="decimal-pad" />
        </Section>

        <Section title={t('merchant.openingHours')}>
          <AppText variant="caption" color="textSecondary" style={styles.hoursHint}>
            {t('merchant.hoursHint')}
          </AppText>
          {values.hours.map((day, index) => {
            const weekday = t(`weekdayLong.${(index + 1) as 1 | 2 | 3 | 4 | 5 | 6 | 7}`);
            const update = (patch: Partial<DayHours>) =>
              set(
                'hours',
                values.hours.map((d, i) => (i === index ? { ...d, ...patch } : d)),
              );
            const setRange = (r: number, patch: Partial<TimeRange>) => update({ intervals: day.intervals.map((range, j) => (j === r ? { ...range, ...patch } : range)) });
            return (
              <View key={index} style={styles.dayBlock} testID={`hours-day-${index + 1}`}>
                <View style={styles.dayRow}>
                  <ToggleSwitch value={day.open} onValueChange={(open) => update({ open })} accessibilityLabel={t('merchant.openOn', { day: weekday })} />
                  <AppText variant="body" style={styles.day}>
                    {weekday}
                  </AppText>
                  {!day.open && (
                    <AppText variant="subhead" color="textSecondary">
                      {t('market.closed')}
                    </AppText>
                  )}
                  {day.open && day.intervals.length < MAX_INTERVALS_PER_DAY && (
                    <IconButton
                      icon="plus"
                      label={t('merchant.addInterval', { day: weekday })}
                      onPress={() => update({ intervals: [...day.intervals, { opensAt: day.intervals.at(-1)?.closesAt ?? '14:00', closesAt: '18:00' }] })}
                      testID={`hours-add-${index + 1}`}
                    />
                  )}
                </View>
                {day.open &&
                  day.intervals.map((range, r) => (
                    <View key={r} style={styles.rangeRow}>
                      <View style={styles.times}>
                        <TextInput value={range.opensAt} onChangeText={(opensAt) => setRange(r, { opensAt })} style={styles.time} accessibilityLabel={forSpeech(t('merchant.opensAt', { day: weekday }))} keyboardType="numbers-and-punctuation" maxLength={5} testID={`hours-${index + 1}-${r}-opens`} />
                        <AppText variant="body">–</AppText>
                        <TextInput value={range.closesAt} onChangeText={(closesAt) => setRange(r, { closesAt })} style={styles.time} accessibilityLabel={forSpeech(t('merchant.closesAt', { day: weekday }))} keyboardType="numbers-and-punctuation" maxLength={5} testID={`hours-${index + 1}-${r}-closes`} />
                      </View>
                      {endsNextDay(range) && (
                        <AppText variant="caption" color="textSecondary" testID={`hours-${index + 1}-${r}-next-day`}>
                          {t('merchant.nextDay')}
                        </AppText>
                      )}
                      <View style={styles.flex} />
                      {day.intervals.length > 1 && (
                        <IconButton icon="close" label={t('merchant.removeInterval', { day: weekday })} onPress={() => update({ intervals: day.intervals.filter((_, j) => j !== r) })} testID={`hours-remove-${index + 1}-${r}`} />
                      )}
                    </View>
                  ))}
              </View>
            );
          })}
          {err('hours') !== undefined && <AppText variant="caption" color="accent">{err('hours')}</AppText>}
        </Section>

        <Section title={t('merchant.tags')}>
          <ChipWrap label={t('merchant.tags')}>
            {TAGS.map((tag) => (
              <Chip key={tag} label={t(`merchant.tag.${tag}`)} selected={values.tags.includes(tag)} onPress={() => set('tags', toggle(values.tags, tag) as MarketTag[])} />
            ))}
          </ChipWrap>
        </Section>

        <Section title={t('merchant.images')}>
          {marketId !== undefined ? (
            <>
              <PhotoField label={t('merchant.logo')} marketId={marketId} purpose="LOGO" value={values.logoUrl === '' ? null : values.logoUrl} onChange={(url) => set('logoUrl', url ?? '')} placeholderIcon="store" testID="logo-photo" />
              <PhotoField label={t('merchant.cover')} marketId={marketId} purpose="COVER" aspectRatio={16 / 9} value={values.coverImageUrl === '' ? null : values.coverImageUrl} onChange={(url) => set('coverImageUrl', url ?? '')} testID="cover-photo" />
              {(err('logoUrl') ?? err('coverImageUrl')) !== undefined && (
                <AppText variant="caption" color="accent">
                  {err('logoUrl') ?? err('coverImageUrl')}
                </AppText>
              )}
            </>
          ) : (
            <AppText variant="subhead" color="textSecondary" testID="photos-after-create">
              {t('merchant.photo.afterCreate')}
            </AppText>
          )}
        </Section>

        <Section title={t('merchant.imprint')}>
          <TextField label={t('merchant.legalName')} {...text('legalName')} />
          <TextField label={t('merchant.registerInfo')} {...text('registerInfo')} hint={t('merchant.optional')} />
        </Section>
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 48, gap: t.spacing.md },
  flex: { flex: 1 },
  dayBlock: { paddingVertical: t.spacing.xs, borderBottomWidth: 1, borderBottomColor: t.colors.separator },
  dayRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm, minHeight: 52 },
  rangeRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm, minHeight: 48, paddingStart: 60 },
  hoursHint: { marginBottom: t.spacing.xs },
  day: { flex: 1 },
  times: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.xs, direction: 'ltr' as const },
  time: { ...t.type.body, color: t.colors.text, backgroundColor: t.colors.surfaceMuted, borderRadius: t.radii.label, minWidth: 68, minHeight: 44, textAlign: 'center' as const, paddingHorizontal: t.spacing.sm },
  formError: { marginBottom: t.spacing.sm },
}));
