import { useState } from 'react';
import { ScrollView } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type ListingDetails } from '../../../shared/api';
import { isLanguage, type Language } from '../../../shared/i18n';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader, TextField, queryState } from '../../../shared/ui';
import { hasErrors } from '../../auth/validation';
import { useListingDetails, useMerchantLanguages, useMyMarkets, useUpdateListing } from '../api/hooks';
import { PhotoField } from '../components/PhotoField';
import { ProductFields } from '../components/ProductFields';
import {
  fromCatalogListing,
  fromListingDetails,
  toCatalogListingUpdate,
  toProductUpdate,
  validateCatalogListing,
  validateNewProduct,
  withoutOutdatedTranslations,
  type CatalogListingField,
  type CatalogListingValues,
  type Errors,
  type NewProductField,
  type NewProductValues,
} from '../validation';

/**
 * Edit a listing completely. The market's own product: every detail (names and descriptions in
 * every language, search words, pack, category, cuisines, allergens, barcode, photo, price, limit).
 * A shared catalog product: the market's own name, description, pack and brand, photo, price, limit.
 */
export function EditListingScreen({ route, navigation }: RootStackScreenProps<'MerchantEditListing'>) {
  const { t } = useTranslation();
  const { marketId, listingId } = route.params;
  const details = useListingDetails(marketId, listingId);
  const market = useMyMarkets().data?.find((m) => m.id === marketId);
  const primaryLocale: Language = isLanguage(market?.primaryLocale) ? market.primaryLocale : 'de';
  return (
    <Screen padded={false} header={<ScreenHeader title={t('merchant.editTitle')} onBack={() => navigation.goBack()} />}>
      {queryState(details) ??
        (details.data !== undefined &&
          (details.data.listing.privateProduct ? (
            <OwnProductForm key={listingId} details={details.data} marketId={marketId} primaryLocale={primaryLocale} onSaved={() => navigation.goBack()} />
          ) : (
            <CatalogListingForm key={listingId} details={details.data} marketId={marketId} primaryLocale={primaryLocale} onSaved={() => navigation.goBack()} />
          )))}
    </Screen>
  );
}

interface FormProps {
  details: ListingDetails;
  marketId: string;
  primaryLocale: Language;
  onSaved: () => void;
}

function OwnProductForm({ details, marketId, primaryLocale, onSaved }: FormProps) {
  const styles = useStyles();
  const { t } = useTranslation();
  const update = useUpdateListing(marketId);
  const { multilingual } = useMerchantLanguages(primaryLocale);
  const [initial] = useState<NewProductValues>(() => fromListingDetails(details));
  const [values, setValues] = useState<NewProductValues>(initial);
  const [errors, setErrors] = useState<Errors<NewProductField>>({});
  const set = <K extends keyof NewProductValues>(key: K, value: NewProductValues[K]) => setValues((v) => ({ ...v, [key]: value }));
  const err = (field: NewProductField) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };
  const submit = () => {
    const found = validateNewProduct(values, primaryLocale);
    setErrors(found);
    if (!hasErrors(found)) {
      const sent = multilingual ? values : withoutOutdatedTranslations(values, initial, primaryLocale);
      update.mutate({ productId: details.listing.id, request: toProductUpdate(sent) }, { onSuccess: onSaved });
    }
  };
  return (
    <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled" testID="edit-own-product">
      <ProductFields marketId={marketId} primaryLocale={primaryLocale} values={values} onChange={set} error={err} idPrefix="edit" />
      {(hasErrors(errors) || update.isError) && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert">
          {update.isError ? t(errorMessageKey(update.error)) : t('validation.checkFields')}
        </AppText>
      )}
      <Button title={t('common.save')} onPress={submit} loading={update.isPending} testID="edit-save" />
    </ScrollView>
  );
}

function CatalogListingForm({ details, marketId, primaryLocale, onSaved }: FormProps) {
  const styles = useStyles();
  const { t } = useTranslation();
  const update = useUpdateListing(marketId);
  const [values, setValues] = useState<CatalogListingValues>(() => fromCatalogListing(details.listing));
  const [errors, setErrors] = useState<Errors<CatalogListingField>>({});
  const set = <K extends keyof CatalogListingValues>(key: K, value: CatalogListingValues[K]) => setValues((v) => ({ ...v, [key]: value }));
  const err = (field: CatalogListingField) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };
  const product = details.product;
  const catalogName = product.name[primaryLocale] ?? product.name.de ?? Object.values(product.name)[0] ?? '';
  const catalogDescription = product.description?.[primaryLocale] ?? product.description?.de ?? '';
  const submit = () => {
    const found = validateCatalogListing(values);
    setErrors(found);
    if (!hasErrors(found)) {
      update.mutate({ productId: details.listing.id, request: toCatalogListingUpdate(values) }, { onSuccess: onSaved });
    }
  };
  return (
    <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled" testID="edit-catalog-listing">
      <AppText variant="subhead" color="textSecondary">
        {t('merchant.catalogHint')}
      </AppText>
      <TextField label={t('merchant.ownName')} value={values.name} onChangeText={(v) => set('name', v)} error={err('name')} placeholder={catalogName} hint={t('merchant.catalogValue', { value: catalogName })} testID="edit-own-name" />
      <TextField
        label={t('merchant.ownDescription')}
        value={values.description}
        onChangeText={(v) => set('description', v)}
        error={err('description')}
        placeholder={catalogDescription}
        multiline
        testID="edit-own-description"
      />
      <TextField label={t('merchant.unitSize')} value={values.unitSize} onChangeText={(v) => set('unitSize', v)} error={err('unitSize')} placeholder={product.unitSize} hint={t('merchant.catalogValue', { value: product.unitSize })} testID="edit-own-unit" />
      <TextField
        label={t('merchant.brand')}
        value={values.brand}
        onChangeText={(v) => set('brand', v)}
        error={err('brand')}
        placeholder={product.brand ?? ''}
        {...(product.brand != null ? { hint: t('merchant.catalogValue', { value: product.brand }) } : {})}
        testID="edit-own-brand"
      />
      <PhotoField label={t('merchant.productPhoto')} marketId={marketId} purpose="PRODUCT" value={values.imageUrl} onChange={(url) => set('imageUrl', url)} placeholderIcon="package" testID="edit-photo" />
      <TextField label={t('merchant.price')} value={values.price} onChangeText={(v) => set('price', v)} error={err('price')} keyboardType="decimal-pad" inputDirection="ltr" testID="edit-price" />
      <TextField label={t('merchant.maxQuantity')} value={values.maxOrderQuantity} onChangeText={(v) => set('maxOrderQuantity', v)} error={err('maxOrderQuantity')} keyboardType="number-pad" inputDirection="ltr" testID="edit-max" />
      {(hasErrors(errors) || update.isError) && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert">
          {update.isError ? t(errorMessageKey(update.error)) : t('validation.checkFields')}
        </AppText>
      )}
      <Button title={t('common.save')} onPress={submit} loading={update.isPending} testID="edit-save" />
    </ScrollView>
  );
}

const useStyles = makeStyles((t) => ({
  form: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl, gap: t.spacing.xs },
}));
