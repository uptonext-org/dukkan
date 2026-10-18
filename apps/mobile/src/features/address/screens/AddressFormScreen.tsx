import { Alert } from 'react-native';

import { errorMessage, isApiError } from '../../../shared/api';
import { Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useAddresses, useSaveAddress } from '../api/hooks';
import { AddressForm, NOT_SERVICEABLE_MESSAGE } from '../components/AddressForm';
import { EMPTY_ADDRESS, toAddressRequest, toFormValues } from '../validation';

export function AddressFormScreen({ navigation, route }: RootStackScreenProps<'AddressForm'>) {
  const addressId = route.params?.addressId;
  const selectForDelivery = route.params?.selectForDelivery ?? false;
  const addresses = useAddresses();
  const save = useSaveAddress(addressId);

  if (addressId !== undefined) {
    const state = queryState(addresses);
    if (state) {
      return <Screen>{state}</Screen>;
    }
  }
  const existing = addressId === undefined ? undefined : addresses.data?.find((a) => a.id === addressId);

  return (
    <Screen scroll>
      <AddressForm
        key={existing?.id ?? 'new'}
        initialValues={existing ? toFormValues(existing) : EMPTY_ADDRESS}
        submitLabel={addressId === undefined ? 'Save address' : 'Save changes'}
        submitting={save.isPending}
        serverErrors={isApiError(save.error) ? save.error.fieldErrors : {}}
        formError={save.isError ? errorMessage(save.error) : null}
        onSubmit={(values) =>
          save.mutate(toAddressRequest(values), {
            onSuccess: (address) => {
              if (!address.serviceable) {
                Alert.alert('Address saved', `${NOT_SERVICEABLE_MESSAGE} We will let you know when we do.`);
                navigation.goBack();
              } else if (selectForDelivery) {
                navigation.replace('DeliverySlotPicker', { addressId: address.id });
              } else {
                navigation.goBack();
              }
            },
          })
        }
      />
    </Screen>
  );
}
