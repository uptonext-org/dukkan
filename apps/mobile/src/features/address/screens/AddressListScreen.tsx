import { memo, useCallback } from 'react';
import { Alert, FlatList, StyleSheet, View, type ListRenderItem } from 'react-native';

import type { Address } from '../../../shared/api';
import { spacing } from '../../../shared/theme';
import { Badge, Button, EmptyState, ListRow, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useAddresses, useDeleteAddress } from '../api/hooks';
import { NOT_SERVICEABLE_MESSAGE } from '../components/AddressForm';
import { formatAddressLine } from '../validation';

interface AddressItemProps {
  address: Address;
  selectForDelivery: boolean;
  onSelect: (address: Address) => void;
  onEdit: (address: Address) => void;
  onDelete: (address: Address) => void;
}

const AddressItem = memo(function AddressItem({
  address,
  selectForDelivery,
  onSelect,
  onEdit,
  onDelete,
}: AddressItemProps) {
  const title = address.label ?? formatAddressLine(address);
  return (
    <View>
      <ListRow
        title={title}
        subtitle={address.label != null ? formatAddressLine(address) : undefined}
        onPress={() => (selectForDelivery ? onSelect(address) : onEdit(address))}
        disabled={selectForDelivery && !address.serviceable}
        accessibilityHint={selectForDelivery ? 'Shows delivery slots for this address' : 'Edits this address'}
        right={
          <View style={styles.badges}>
            {address.isDefault && <Badge label="Default" tone="success" />}
            {!address.serviceable && <Badge label={NOT_SERVICEABLE_MESSAGE} tone="warning" />}
          </View>
        }
      />
      {!selectForDelivery && (
        <View style={styles.actions}>
          <Button
            title="Edit"
            variant="ghost"
            onPress={() => onEdit(address)}
            accessibilityLabel={`Edit ${title}`}
          />
          <Button
            title="Delete"
            variant="ghost"
            onPress={() => onDelete(address)}
            accessibilityLabel={`Delete ${title}`}
          />
        </View>
      )}
    </View>
  );
});

export function AddressListScreen({ navigation, route }: RootStackScreenProps<'AddressList'>) {
  const selectForDelivery = route.params?.selectForDelivery ?? false;
  const addresses = useAddresses();
  const deleteAddress = useDeleteAddress();
  const { mutate: remove } = deleteAddress;

  const onSelect = useCallback(
    (address: Address) => navigation.navigate('DeliverySlotPicker', { addressId: address.id }),
    [navigation],
  );
  const onEdit = useCallback(
    (address: Address) => navigation.navigate('AddressForm', { addressId: address.id }),
    [navigation],
  );
  const onDelete = useCallback(
    (address: Address) =>
      Alert.alert('Delete address?', formatAddressLine(address), [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => remove(address.id) },
      ]),
    [remove],
  );
  const renderItem = useCallback<ListRenderItem<Address>>(
    ({ item }) => (
      <AddressItem
        address={item}
        selectForDelivery={selectForDelivery}
        onSelect={onSelect}
        onEdit={onEdit}
        onDelete={onDelete}
      />
    ),
    [selectForDelivery, onSelect, onEdit, onDelete],
  );

  const state = queryState(addresses);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }
  return (
    <Screen
      padded={false}
      footer={
        <Button
          title="Add new address"
          onPress={() => navigation.navigate('AddressForm', { selectForDelivery })}
        />
      }>
      <FlatList
        data={addresses.data}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        refreshing={addresses.isRefetching}
        onRefresh={() => void addresses.refetch()}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <EmptyState title="No addresses yet" message="Add a delivery address to choose a delivery slot." />
        }
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { flexGrow: 1 },
  badges: { alignItems: 'flex-end', gap: spacing.xs, maxWidth: '45%' },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', paddingHorizontal: spacing.sm },
});
