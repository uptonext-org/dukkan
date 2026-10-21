import { useState } from 'react';
import { FlatList, ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type CatalogProduct } from '../../../shared/api';
import { withOrganicPrefix } from '../../../shared/format';
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { isLanguage, type Language } from '../../../shared/i18n';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Chip, ChipScroller, EmptyState, ErrorState, ImageTile, LoadingState, Screen, ScreenHeader, SearchField, SegmentedControl, TextField, ToggleSwitch, placeholderProps } from '../../../shared/ui';
import { hasErrors } from '../../auth/validation';
import { useAllCategories } from '../../catalog/api/hooks';
import { useCuisines } from '../../marketplace/api/hooks';
import { toggle } from '../../marketplace/model';
import { useCatalogSearch, useCreateListing, useMyMarkets } from '../api/hooks';
import { LocalizedFields } from '../components/LocalizedFields';
import {
  emptyNewProduct,
  toCatalogListing,
  toNewProductListing,
  validateNewProduct,
  validatePrice,
  type Errors,
  type NewProductField,
  type NewProductValues,
} from '../validation';

type Mode = 'catalog' | 'new';

function CatalogRow({ product, marketId, onAdded }: { product: CatalogProduct; marketId: string; onAdded: () => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const create = useCreateListing(marketId);
  const [open, setOpen] = useState(false);
  const [price, setPrice] = useState('');
  const [error, setError] = useState<string | undefined>();
  const listed = product.alreadyListed === true;
  const add = () => {
    const key = validatePrice(price);
    if (key !== undefined) {
      setError(t(`validation.${key}`));
      return;
    }
    setError(undefined);
    create.mutate(toCatalogListing(product.id, price), { onSuccess: onAdded });
  };
  return (
    <View style={styles.catalogRow} testID={`catalog-${product.id}`}>
      <View style={styles.rowMain}>
        <ImageTile uri={product.imageUrl} size={52} radius={10} {...placeholderProps(product.iconKey)} />
        <View style={styles.flex}>
          <AppText variant="body" dense numberOfLines={2}>
            {withOrganicPrefix(product.name, product.organic, t('product.organic'))}
          </AppText>
          <AppText variant="subhead" color="textSecondary" dense>
            {[product.brand, product.unitSize, product.cuisines.map((c) => c.name).join(', ')].filter(Boolean).join(' · ')}
          </AppText>
        </View>
        {listed ? (
          <AppText variant="caption" color="textSecondary">
            {t('merchant.alreadyListed')}
          </AppText>
        ) : (
          <Button title={t('merchant.select')} variant="secondary" onPress={() => setOpen((o) => !o)} accessibilityLabel={t('merchant.selectProduct', { name: product.name })} />
        )}
      </View>
      {open && !listed && (
        <View style={styles.priceRow}>
          <View style={styles.flex}>
            <TextField label={t('merchant.price')} value={price} onChangeText={setPrice} keyboardType="decimal-pad" error={error} inputDirection="ltr" testID={`catalog-price-${product.id}`} />
          </View>
          <Button title={t('merchant.add')} onPress={add} loading={create.isPending} testID={`catalog-add-${product.id}`} />
        </View>
      )}
      {create.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(create.error))}
        </AppText>
      )}
    </View>
  );
}

function NewProductForm({ marketId, primaryLocale, onAdded }: { marketId: string; primaryLocale: Language; onAdded: () => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const categories = useAllCategories();
  const cuisines = useCuisines();
  const create = useCreateListing(marketId);
  const [values, setValues] = useState<NewProductValues>(emptyNewProduct);
  const [errors, setErrors] = useState<Errors<NewProductField>>({});
  const set = <K extends keyof NewProductValues>(key: K, value: NewProductValues[K]) => setValues((v) => ({ ...v, [key]: value }));
  const err = (field: NewProductField) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };
  const submit = () => {
    const found = validateNewProduct(values, primaryLocale);
    setErrors(found);
    if (hasErrors(found)) {
      return;
    }
    create.mutate(toNewProductListing(values), { onSuccess: onAdded });
  };
  return (
    <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled" testID="new-product-form">
      <LocalizedFields label={t('merchant.productName')} values={values.name} primary={primaryLocale} error={err('name')} onChange={(lang, text) => set('name', { ...values.name, [lang]: text })} testIDPrefix="new-name" />
      <TextField label={t('merchant.keywords')} value={values.keywords} onChangeText={(v) => set('keywords', v)} error={err('keywords')} hint={t('merchant.keywordsHint')} />
      <TextField label={t('merchant.brand')} value={values.brand} onChangeText={(v) => set('brand', v)} error={err('brand')} hint={t('merchant.optional')} />
      <TextField label={t('merchant.unitSize')} value={values.unitSize} onChangeText={(v) => set('unitSize', v)} error={err('unitSize')} placeholder="500 g" testID="new-unit" />
      <AppText variant="subhead" color="textSecondary">
        {t('merchant.category')}
      </AppText>
      <View style={styles.wrap}>
        {(categories.data ?? []).map((c) => (
          <Chip key={c.id} label={c.name} selected={values.categoryId === c.id} onPress={() => set('categoryId', c.id)} />
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
      <TextField label={t('merchant.allergens')} value={values.allergens} onChangeText={(v) => set('allergens', v)} hint={t('merchant.allergensHint')} />
      <TextField label={t('merchant.price')} value={values.price} onChangeText={(v) => set('price', v)} error={err('price')} keyboardType="decimal-pad" inputDirection="ltr" testID="new-price" />
      <TextField label={t('merchant.maxQuantity')} value={values.maxOrderQuantity} onChangeText={(v) => set('maxOrderQuantity', v)} error={err('maxOrderQuantity')} keyboardType="number-pad" inputDirection="ltr" />
      {create.isError && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(create.error))}
        </AppText>
      )}
      <Button title={t('merchant.createProduct')} onPress={submit} loading={create.isPending} testID="new-product-submit" />
    </ScrollView>
  );
}

/** "Produkt hinzufügen": pick from the shared catalog (with cuisine filter) or create a new product. */
export function AddProductScreen({ route, navigation }: RootStackScreenProps<'MerchantAddProduct'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = route.params;
  const [mode, setMode] = useState<Mode>('catalog');
  const [text, setText] = useState('');
  const [cuisine, setCuisine] = useState<string | null>(null);
  const q = useDebouncedValue(text.trim(), 300);
  const cuisines = useCuisines();
  const catalog = useCatalogSearch({ marketId, ...(q !== '' ? { q } : {}), ...(cuisine !== null ? { cuisine } : {}), size: 30 });
  const market = useMyMarkets().data?.find((m) => m.id === marketId);
  const primaryLocale: Language = isLanguage(market?.primaryLocale) ? market.primaryLocale : 'de';
  const [added, setAdded] = useState<string | null>(null);
  const onAdded = () => setAdded(t('merchant.added'));

  return (
    <Screen padded={false} header={<ScreenHeader title={t('merchant.addProduct')} onBack={() => navigation.goBack()} />}>
      <View style={styles.top}>
        <SegmentedControl
          accessibilityLabel={t('merchant.addMode')}
          segments={[
            { value: 'catalog', label: t('merchant.fromCatalog') },
            { value: 'new', label: t('merchant.newProduct') },
          ]}
          value={mode}
          onChange={setMode}
        />
        {added !== null && (
          <AppText variant="subhead" color="primary" accessibilityRole="alert" accessibilityLiveRegion="polite" testID="listing-added">
            {added}
          </AppText>
        )}
      </View>
      {mode === 'new' ? (
        <NewProductForm marketId={marketId} primaryLocale={primaryLocale} onAdded={onAdded} />
      ) : (
        <>
          <View style={styles.searchBox}>
            <SearchField value={text} onChangeText={setText} placeholder={t('merchant.searchCatalog')} testID="catalog-search" />
          </View>
          <ChipScroller style={styles.chipsWrap} accessibilityLabel={t('merchant.cuisineFilter')} testID="cuisine-filters">
            <Chip label={t('discover.all')} selected={cuisine === null} onPress={() => setCuisine(null)} />
            {(cuisines.data ?? []).map((c) => (
              <Chip key={c.code} label={c.name} selected={cuisine === c.code} onPress={() => setCuisine(cuisine === c.code ? null : c.code)} testID={`cuisine-filter-${c.code}`} />
            ))}
          </ChipScroller>
          {catalog.isPending ? (
            <LoadingState />
          ) : catalog.isError ? (
            <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} />
          ) : (
            <FlatList
              data={catalog.products}
              keyExtractor={(p) => p.id}
              contentContainerStyle={styles.list}
              keyboardShouldPersistTaps="handled"
              renderItem={({ item }) => <CatalogRow product={item} marketId={marketId} onAdded={onAdded} />}
              onEndReached={() => {
                if (catalog.hasNextPage && !catalog.isFetchingNextPage) {
                  void catalog.fetchNextPage();
                }
              }}
              ListEmptyComponent={
                <EmptyState icon="package" title={t('merchant.catalogEmptyTitle')} message={t('merchant.catalogEmptyMessage')} actionLabel={t('merchant.newProduct')} onAction={() => setMode('new')} />
              }
            />
          )}
        </>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  top: { paddingHorizontal: t.layout.gutter, gap: t.spacing.sm, paddingBottom: t.spacing.sm },
  searchBox: { paddingHorizontal: t.layout.gutter },
  chipsWrap: { marginVertical: t.spacing.sm },
  list: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl, flexGrow: 1 },
  catalogRow: { paddingVertical: t.spacing.sm, borderBottomWidth: 1, borderBottomColor: t.colors.separator, gap: t.spacing.sm },
  rowMain: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  priceRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  flex: { flex: 1 },
  form: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl, gap: t.spacing.xs },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: t.spacing.sm },
  gapTop: { marginTop: t.spacing.md },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: t.spacing.md },
}));
