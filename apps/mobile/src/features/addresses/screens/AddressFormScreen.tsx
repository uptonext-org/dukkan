import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, type Place } from '../../../shared/api';
import { PlaceSearch } from '../../../shared/location';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, queryState, Screen, ScreenHeader, TextField, ToggleSwitch } from '../../../shared/ui';
import { hasErrors } from '../../auth/validation';
import { useAddresses, useSaveAddress } from '../api/hooks';
import { EMPTY_ADDRESS, toAddressRequest, toFormValues, validateAddress, type AddressErrors, type AddressField, type AddressFormValues } from '../validation';

/** Address form. Searching an address (/geo/search) fills street, postal code, city and the location. */
export function AddressFormScreen(props: RootStackScreenProps<'AddressForm'>) {
  const addressId = props.route.params?.addressId;
  const addresses = useAddresses();
  if (addressId !== undefined && addresses.isPending) {
    return <Screen>{queryState(addresses)}</Screen>;
  }
  const existing = addresses.data?.find((a) => a.id === addressId);
  return <AddressForm {...props} initial={existing !== undefined ? toFormValues(existing) : EMPTY_ADDRESS} />;
}

function AddressForm({ route, navigation, initial }: RootStackScreenProps<'AddressForm'> & { initial: AddressFormValues }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const addressId = route.params?.addressId;
  const [values, setValues] = useState<AddressFormValues>(initial);
  const [errors, setErrors] = useState<AddressErrors>({});
  const save = useSaveAddress(addressId);

  const set = (field: AddressField) => (text: string) => {
    setValues((v) => ({ ...v, [field]: text, ...(field === 'street' || field === 'postalCode' ? { location: null } : {}) }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };
  const fromPlace = (place: Place) =>
    setValues((v) => ({
      ...v,
      street: place.street ?? v.street,
      houseNumber: place.houseNumber ?? v.houseNumber,
      postalCode: place.postalCode ?? v.postalCode,
      city: place.city,
      location: place.location,
    }));
  const err = (field: AddressField) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };

  const submit = () => {
    const found = validateAddress(values);
    setErrors(found);
    if (hasErrors(found)) {
      return;
    }
    save.mutate(toAddressRequest(values), { onSuccess: () => navigation.goBack() });
  };

  return (
    <Screen
      scroll
      header={<ScreenHeader title={addressId !== undefined ? t('address.edit') : t('address.new')} onBack={() => navigation.goBack()} />}
      footer={<Button title={t('common.save')} onPress={submit} loading={save.isPending} testID="address-save" />}>
      <AppText variant="subhead" color="textSecondary" style={styles.label}>
        {t('address.search')}
      </AppText>
      <PlaceSearch onSelect={fromPlace} placeholder={t('address.searchPlaceholder')} />
      {values.location !== null && (
        <AppText variant="caption" color="primary" style={styles.located}>
          {t('address.located')}
        </AppText>
      )}
      <View style={styles.gap} />
      <TextField label={t('address.label')} value={values.label} onChangeText={set('label')} error={err('label')} placeholder={t('address.labelPlaceholder')} />
      <TextField label={t('address.street')} value={values.street} onChangeText={set('street')} error={err('street')} autoComplete="street-address" />
      <TextField label={t('address.houseNumber')} value={values.houseNumber} onChangeText={set('houseNumber')} error={err('houseNumber')} />
      <TextField label={t('address.addition')} value={values.addition} onChangeText={set('addition')} error={err('addition')} />
      <TextField label={t('address.postalCode')} value={values.postalCode} onChangeText={set('postalCode')} error={err('postalCode')} keyboardType="number-pad" maxLength={5} autoComplete="postal-code" />
      <TextField label={t('address.city')} value={values.city} onChangeText={set('city')} error={err('city')} />
      <TextField label={t('address.instructions')} value={values.deliveryInstructions} onChangeText={set('deliveryInstructions')} error={err('deliveryInstructions')} multiline />
      <View style={styles.switchRow}>
        <AppText variant="body" style={styles.flex}>
          {t('address.makeDefault')}
        </AppText>
        <ToggleSwitch
          value={values.isDefault}
          onValueChange={(isDefault) => setValues((v) => ({ ...v, isDefault }))}
          accessibilityLabel={t('address.makeDefault')}
        />
      </View>
      {save.isError && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(save.error))}
        </AppText>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  label: { marginBottom: t.spacing.xs },
  located: { marginTop: t.spacing.xs },
  gap: { height: t.spacing.lg },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: t.spacing.md },
  flex: { flex: 1 },
}));
