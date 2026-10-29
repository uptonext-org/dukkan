import { memo, useState } from 'react';
import { FlatList, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type Listing } from '../../../shared/api';
import { centsToInput, formatMoney, forSpeech, parseMoneyInput } from '../../../shared/format';
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, EmptyState, IconButton, ImageTile, Screen, ScreenHeader, SearchField, ToggleSwitch, placeholderProps, queryState } from '../../../shared/ui';
import { useListings, useUpdateListing } from '../api/hooks';
import { PhotoField } from '../components/PhotoField';

const ListingRow = memo(function ListingRow({ listing, marketId, onEdit }: { listing: Listing; marketId: string; onEdit: (listingId: string) => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const update = useUpdateListing(marketId);
  const [editing, setEditing] = useState(false);
  const [photo, setPhoto] = useState(false);
  const [price, setPrice] = useState(centsToInput(listing.price.amountCents));
  const cents = parseMoneyInput(price);
  return (
    <View style={styles.row}>
      <View style={styles.main}>
        <ImageTile uri={listing.imageUrl} size={52} radius={10} {...placeholderProps(listing.iconKey)} />
        <View style={styles.flex}>
          <AppText variant="body" dense numberOfLines={2}>
            {listing.name}
          </AppText>
          <AppText variant="subhead" color="textSecondary" dense>
            {`${listing.unitSize} · ${formatMoney(listing.price)}${listing.promotion != null ? ` · ${listing.promotion.label}` : ''}`}
          </AppText>
        </View>
        <IconButton icon="camera" label={t('merchant.photo.editFor', { name: listing.name })} onPress={() => setPhoto((p) => !p)} selected={photo} testID={`photo-toggle-${listing.id}`} />
        <IconButton icon="tag" label={t('merchant.editPrice', { name: listing.name })} onPress={() => setEditing((e) => !e)} selected={editing} testID={`price-toggle-${listing.id}`} />
        <IconButton icon="edit" label={t('merchant.editProduct', { name: listing.name })} onPress={() => onEdit(listing.id)} testID={`edit-${listing.id}`} />
        <ToggleSwitch
          value={listing.available}
          disabled={update.isPending}
          onValueChange={(available) => update.mutate({ productId: listing.id, request: { available } })}
          accessibilityLabel={forSpeech(t('merchant.availability', { name: listing.name }))}
          testID={`availability-${listing.id}`}
        />
      </View>
      {editing && (
        <View style={styles.editRow}>
          <TextInput
            value={price}
            onChangeText={setPrice}
            keyboardType="decimal-pad"
            style={styles.priceInput}
            accessibilityLabel={forSpeech(t('merchant.priceFor', { name: listing.name }))}
          />
          <AppText variant="body">€</AppText>
          <Button
            title={t('common.save')}
            variant="secondary"
            disabled={cents === null || cents <= 0}
            loading={update.isPending}
            onPress={() => {
              if (cents !== null) {
                update.mutate({ productId: listing.id, request: { price: { amountCents: cents, currency: listing.price.currency } } }, { onSuccess: () => setEditing(false) });
              }
            }}
          />
        </View>
      )}
      {photo && (
        <PhotoField
          label={t('merchant.productPhoto')}
          marketId={marketId}
          purpose="PRODUCT"
          value={listing.imageUrl ?? null}
          saving={update.isPending}
          onChange={(imageUrl) => update.mutate({ productId: listing.id, request: { imageUrl } })}
          placeholderIcon="package"
          testID={`listing-photo-${listing.id}`}
        />
      )}
      {update.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(update.error))}
        </AppText>
      )}
    </View>
  );
});

/** Listings with an availability toggle, a quick price edit and a full edit screen per product. */
export function ListingsScreen({ route, navigation }: RootStackScreenProps<'MerchantListings'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = route.params;
  const [text, setText] = useState('');
  const q = useDebouncedValue(text.trim(), 300);
  const query = useListings(marketId, q);
  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={t('merchant.listings')} onBack={() => navigation.goBack()} />}
      footer={<Button title={t('merchant.addProduct')} icon="plus" onPress={() => navigation.navigate('MerchantAddProduct', { marketId })} testID="listings-add" />}>
      <View style={styles.search}>
        <SearchField value={text} onChangeText={setText} placeholder={t('merchant.searchListings')} />
      </View>
      {queryState(query) ?? (
        <FlatList
          data={query.listings}
          keyExtractor={(l) => l.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => <ListingRow listing={item} marketId={marketId} onEdit={(listingId) => navigation.navigate('MerchantEditListing', { marketId, listingId })} />}
          onEndReached={() => {
            if (query.hasNextPage && !query.isFetchingNextPage) {
              void query.fetchNextPage();
            }
          }}
          ListEmptyComponent={<EmptyState icon="package" title={t('merchant.noListingsTitle')} message={t('merchant.noListingsMessage')} />}
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  search: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.sm },
  list: { paddingHorizontal: t.layout.gutter, flexGrow: 1, paddingBottom: t.spacing.xxxl },
  row: { paddingVertical: t.spacing.sm, borderBottomWidth: 1, borderBottomColor: t.colors.separator, gap: t.spacing.sm },
  main: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
  flex: { flex: 1 },
  editRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
  priceInput: { ...t.type.body, color: t.colors.text, backgroundColor: t.colors.surfaceMuted, borderRadius: t.radii.pill, minHeight: 44, minWidth: 100, paddingHorizontal: t.spacing.md, direction: 'ltr' as const },
}));
