import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ALLERGEN_CODES } from '../../../shared/format';
import type { Language } from '../../../shared/i18n';
import { makeStyles } from '../../../shared/theme';
import { AppText, Chip, TextField, ToggleSwitch } from '../../../shared/ui';
import { useAllCategories } from '../../catalog/api/hooks';
import { useCuisines } from '../../marketplace/api/hooks';
import { toggle } from '../../marketplace/model';
import type { NewProductField, NewProductValues } from '../validation';
import { LocalizedFields } from './LocalizedFields';
import { PhotoField } from './PhotoField';

interface ProductFieldsProps {
  marketId: string;
  primaryLocale: Language;
  values: NewProductValues;
  onChange: <K extends keyof NewProductValues>(key: K, value: NewProductValues[K]) => void;
  /** Translated message of a field's error, if any. */
  error: (field: NewProductField) => string | undefined;
  /** testID prefix of the fields, e.g. `new` or `edit`. */
  idPrefix: string;
}

/** Every field of a market's own product: names and descriptions, search words, pack, category, cuisines, allergens, photo, price. */
export function ProductFields({ marketId, primaryLocale, values, onChange: set, error: err, idPrefix }: ProductFieldsProps) {
  const styles = useStyles();
  const { t } = useTranslation();
  const categories = useAllCategories();
  const cuisines = useCuisines();
  return (
    <>
      <LocalizedFields label={t('merchant.productName')} values={values.name} primary={primaryLocale} error={err('name')} onChange={(lang, text) => set('name', { ...values.name, [lang]: text })} testIDPrefix={`${idPrefix}-name`} translateKind="PRODUCT_NAME" />
      <LocalizedFields label={t('merchant.description')} values={values.description} primary={primaryLocale} error={err('description')} onChange={(lang, text) => set('description', { ...values.description, [lang]: text })} multiline testIDPrefix={`${idPrefix}-description`} translateKind="PRODUCT_DESCRIPTION" />
      <TextField label={t('merchant.keywords')} value={values.keywords} onChangeText={(v) => set('keywords', v)} error={err('keywords')} hint={t('merchant.keywordsHint')} />
      <TextField label={t('merchant.brand')} value={values.brand} onChangeText={(v) => set('brand', v)} error={err('brand')} hint={t('merchant.optional')} testID={`${idPrefix}-brand`} />
      <TextField label={t('merchant.unitSize')} value={values.unitSize} onChangeText={(v) => set('unitSize', v)} error={err('unitSize')} placeholder="500 g" testID={`${idPrefix}-unit`} />
      <AppText variant="subhead" color="textSecondary">
        {t('merchant.category')}
      </AppText>
      <View style={styles.wrap}>
        {(categories.data ?? []).map((c) => (
          <Chip key={c.id} label={c.name} selected={values.categoryId === c.id} onPress={() => set('categoryId', c.id)} testID={`${idPrefix}-category-${c.id}`} />
        ))}
      </View>
      {err('categoryId') !== undefined && (
        <AppText variant="caption" color="accent" accessibilityRole="alert">
          {err('categoryId')}
        </AppText>
      )}
      <AppText variant="subhead" color="textSecondary" style={styles.gapTop}>
        {t('merchant.cuisines')}
      </AppText>
      <View style={styles.wrap}>
        {(cuisines.data ?? []).map((c) => (
          <Chip key={c.code} label={c.name} selected={values.cuisineCodes.includes(c.code)} onPress={() => set('cuisineCodes', toggle(values.cuisineCodes, c.code))} />
        ))}
      </View>
      <View style={styles.switchRow}>
        <AppText variant="body" style={styles.flex}>
          {t('merchant.organic')}
        </AppText>
        <ToggleSwitch value={values.organic} onValueChange={(v) => set('organic', v)} accessibilityLabel={t('merchant.organic')} />
      </View>
      <AppText variant="subhead" color="textSecondary" style={styles.gapTop}>
        {t('merchant.allergens')}
      </AppText>
      <AppText variant="caption" color="textSecondary">
        {t('merchant.allergensHint')}
      </AppText>
      <View style={styles.wrap} testID={`${idPrefix}-allergens`}>
        {ALLERGEN_CODES.map((code) => (
          <Chip key={code} label={t(`allergen.${code}`)} selected={values.allergens.includes(code)} onPress={() => set('allergens', toggle(values.allergens, code))} testID={`${idPrefix === 'new' ? '' : `${idPrefix}-`}allergen-${code}`} />
        ))}
      </View>
      <TextField
        label={t('merchant.barcode')}
        value={values.barcode ?? ''}
        onChangeText={(v) => set('barcode', v.trim() === '' ? null : v.replace(/\s/g, ''))}
        error={err('barcode')}
        hint={t('merchant.optional')}
        keyboardType="number-pad"
        inputDirection="ltr"
        testID={`${idPrefix}-barcode`}
      />
      <PhotoField label={t('merchant.productPhoto')} marketId={marketId} purpose="PRODUCT" value={values.imageUrl} onChange={(url) => set('imageUrl', url)} placeholderIcon="package" testID={`${idPrefix}-photo`} />
      <TextField label={t('merchant.price')} value={values.price} onChangeText={(v) => set('price', v)} error={err('price')} keyboardType="decimal-pad" inputDirection="ltr" testID={`${idPrefix}-price`} />
      <TextField label={t('merchant.maxQuantity')} value={values.maxOrderQuantity} onChangeText={(v) => set('maxOrderQuantity', v)} error={err('maxOrderQuantity')} keyboardType="number-pad" inputDirection="ltr" testID={`${idPrefix}-max`} />
    </>
  );
}

const useStyles = makeStyles((t) => ({
  flex: { flex: 1 },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: t.spacing.sm },
  gapTop: { marginTop: t.spacing.md },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: t.spacing.md },
}));
