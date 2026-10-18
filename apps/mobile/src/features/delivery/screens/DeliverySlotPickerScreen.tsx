import { StyleSheet, Text } from 'react-native';

import { errorMessage, isApiError } from '../../../shared/api';
import { colors, spacing, typography } from '../../../shared/theme';
import { Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useCart, useReserveSlot } from '../../cart/api/hooks';
import { useDeliverySlots } from '../api/hooks';
import { DeliverySlotList } from '../components/DeliverySlotList';

export function DeliverySlotPickerScreen({ navigation, route }: RootStackScreenProps<'DeliverySlotPicker'>) {
  const { addressId } = route.params;
  const slots = useDeliverySlots(addressId);
  const cart = useCart();
  const reserve = useReserveSlot();

  const state = queryState(slots);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }

  const selectedSlotId =
    cart.data?.delivery?.addressId === addressId ? cart.data.delivery.slot.id : undefined;

  return (
    <Screen padded={false}>
      <Text style={styles.intro}>Times are shown in German time (Europe/Berlin).</Text>
      {reserve.isError && (
        <Text style={styles.error} accessibilityRole="alert" accessibilityLiveRegion="assertive">
          {errorMessage(reserve.error)}
        </Text>
      )}
      <DeliverySlotList
        slots={slots.data ?? []}
        selectedSlotId={selectedSlotId}
        disabled={reserve.isPending}
        onSelect={(slot) =>
          reserve.mutate(
            { slotId: slot.id, addressId },
            {
              onSuccess: () => navigation.popTo('Tabs', { screen: 'Cart' }),
              onError: (error) => {
                if (isApiError(error) && error.code === 'SLOT_FULL') {
                  void slots.refetch();
                }
              },
            },
          )
        }
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  intro: {
    ...typography.caption,
    color: colors.textMuted,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
  },
  error: { ...typography.body, color: colors.danger, paddingHorizontal: spacing.lg, paddingTop: spacing.sm },
});
