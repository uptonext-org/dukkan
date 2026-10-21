import { Alert, FlatList } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { makeStyles } from '../../../shared/theme';
import { Button, EmptyState, IconButton, ListRow, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { useAddresses, useDeleteAddress } from '../api/hooks';
import { formatAddressLine } from '../validation';

export function AddressListScreen({ navigation }: RootStackScreenProps<'Addresses'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const query = useAddresses();
  const remove = useDeleteAddress();
  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={t('profile.addresses')} onBack={() => navigation.goBack()} />}
      footer={<Button title={t('address.add')} icon="plus" onPress={() => navigation.navigate('AddressForm')} />}>
      {queryState(query) ?? (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(a) => a.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<EmptyState icon="home" title={t('address.emptyTitle')} message={t('address.emptyMessage')} />}
          renderItem={({ item }) => (
            <ListRow
              title={item.label ?? formatAddressLine(item)}
              subtitle={[item.label != null ? formatAddressLine(item) : null, item.isDefault ? t('address.default') : null].filter(Boolean).join(' · ')}
              chevron
              onPress={() => navigation.navigate('AddressForm', { addressId: item.id })}
              trailing={
                <IconButton
                  icon="trash"
                  label={t('address.delete')}
                  onPress={() =>
                    Alert.alert(t('address.deleteTitle'), formatAddressLine(item), [
                      { text: t('common.cancel'), style: 'cancel' },
                      { text: t('address.delete'), style: 'destructive', onPress: () => remove.mutate(item.id) },
                    ])
                  }
                />
              }
            />
          )}
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  list: { paddingHorizontal: t.layout.gutter, flexGrow: 1 },
}));
