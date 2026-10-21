import { useNavigation, type NavigationProp } from '@react-navigation/native';
import { useEffect, useMemo, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackParamList } from '../../../navigation/types';
import { errorMessageKey, type FulfilmentType, type MarketSummary, type Slot } from '../../../shared/api';
import { formatMoney, formatTimeRange } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Chip, Icon, ListRow, SegmentedControl, Sheet } from '../../../shared/ui';
import { useAddresses } from '../../addresses/api/hooks';
import { formatAddressLine } from '../../addresses/validation';
import { useReserveFulfilment, useSlots } from '../api/hooks';
import { groupSlotsByDay, offeredTypes } from '../model';

interface FulfilmentSheetProps {
  visible: boolean;
  onClose: () => void;
  market: MarketSummary;
  current?: { type: FulfilmentType; slotId: string; addressId?: string | null } | null;
}

/**
 * Lieferung | Abholung (only what the market offers), an address picker for delivery, day chips
 * and slot rows with fee and remaining capacity. Unavailable windows are disabled and announced.
 */
export function FulfilmentSheet({ visible, onClose, market, current }: FulfilmentSheetProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const navigation = useNavigation<NavigationProp<RootStackParamList>>();
  const types = offeredTypes(market.fulfilment);
  const [type, setType] = useState<FulfilmentType>(current?.type ?? types[0] ?? 'PICKUP');
  const addresses = useAddresses();
  const defaultAddressId = addresses.data?.find((a) => a.isDefault)?.id ?? addresses.data?.[0]?.id;
  const [addressId, setAddressId] = useState<string | undefined>(current?.addressId ?? undefined);
  const effectiveAddressId = addressId ?? defaultAddressId;
  const slots = useSlots(market.id, type, type === 'DELIVERY' ? effectiveAddressId : undefined, visible);
  const days = useMemo(() => groupSlotsByDay(slots.data ?? []), [slots.data]);
  const [dayKey, setDayKey] = useState<string | null>(null);
  const day = days.find((d) => d.key === dayKey) ?? days.find((d) => d.hasAvailability) ?? days[0];
  const reserve = useReserveFulfilment(market.id);

  useEffect(() => {
    if (!visible) {
      reserve.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset only when the sheet closes
  }, [visible]);

  const choose = (slot: Slot) => {
    reserve.mutate(
      { type, slotId: slot.id, addressId: type === 'DELIVERY' ? (effectiveAddressId ?? null) : null },
      { onSuccess: onClose },
    );
  };

  return (
    <Sheet visible={visible} onClose={onClose} title={t('fulfilment.title')} testID="fulfilment-sheet">
      {types.length > 1 ? (
        <SegmentedControl
          accessibilityLabel={t('fulfilment.typeLabel')}
          segments={types.map((value) => ({
            value,
            label: value === 'DELIVERY' ? t('fulfilment.delivery') : t('fulfilment.pickup'),
            icon: value === 'DELIVERY' ? 'truck' as const : 'store' as const,
          }))}
          value={type}
          onChange={(next) => {
            setType(next);
            setDayKey(null);
          }}
        />
      ) : (
        <View style={styles.single} testID="fulfilment-single-type">
          <Icon name={type === 'DELIVERY' ? 'truck' : 'store'} color={colors.primary} />
          <AppText variant="bodyStrong">{type === 'DELIVERY' ? t('fulfilment.deliveryOnly') : t('fulfilment.pickupOnly')}</AppText>
        </View>
      )}

      {type === 'PICKUP' && (
        <AppText variant="subhead" color="textSecondary" style={styles.section}>
          {t('fulfilment.pickupAt', { address: market.addressLine })}
        </AppText>
      )}

      {type === 'DELIVERY' && (
        <View style={styles.section}>
          <AppText variant="subhead" color="textSecondary" accessibilityRole="header">
            {t('fulfilment.address')}
          </AppText>
          <View accessibilityRole="radiogroup">
            {(addresses.data ?? []).map((address) => {
              const selected = address.id === effectiveAddressId;
              return (
                <ListRow
                  key={address.id}
                  title={address.label ?? formatAddressLine(address)}
                  {...(address.label != null ? { subtitle: formatAddressLine(address) } : {})}
                  chevron={false}
                  selected={selected}
                  onPress={() => {
                    setAddressId(address.id);
                    setDayKey(null);
                  }}
                  trailing={selected ? <Icon name="check" color={colors.primary} /> : undefined}
                />
              );
            })}
          </View>
          <Button
            title={t('fulfilment.addAddress')}
            variant="ghost"
            icon="plus"
            onPress={() => {
              onClose();
              navigation.navigate('AddressForm');
            }}
          />
        </View>
      )}

      {slots.isError && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.section}>
          {t(errorMessageKey(slots.error))}
        </AppText>
      )}
      {slots.isPending && slots.fetchStatus === 'fetching' && (
        <AppText variant="subhead" color="textSecondary" style={styles.section}>
          {t('common.loading')}
        </AppText>
      )}
      {slots.isSuccess && days.length === 0 && (
        <AppText variant="subhead" color="textSecondary" style={styles.section}>
          {t('fulfilment.noSlots')}
        </AppText>
      )}

      {days.length > 0 && day !== undefined && (
        <>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.days} style={styles.section}>
            {days.map((d) => (
              <Chip
                key={d.key}
                label={`${t(`weekday.${d.weekday}`)} ${d.date}`}
                selected={d.key === day.key}
                disabled={!d.hasAvailability}
                onPress={() => setDayKey(d.key)}
              />
            ))}
          </ScrollView>
          {day.slots.map((slot) => {
            const time = formatTimeRange(slot.startsAt, slot.endsAt);
            const fee = slot.fee.amountCents === 0 ? t('fulfilment.free') : formatMoney(slot.fee);
            const capacity = slot.available ? t('fulfilment.remaining', { count: slot.remainingCapacity }) : t('fulfilment.unavailable');
            const selected = current?.slotId === slot.id;
            return (
              <ListRow
                key={slot.id}
                title={time}
                subtitle={`${fee} · ${capacity}`}
                chevron={false}
                disabled={!slot.available || reserve.isPending}
                selected={selected}
                onPress={() => choose(slot)}
                accessibilityLabel={`${time}, ${fee}, ${capacity}`}
                trailing={selected ? <Icon name="check" color={colors.primary} /> : undefined}
                testID={`slot-${slot.id}`}
              />
            );
          })}
        </>
      )}
      {reserve.isError && (
        <AppText variant="subhead" color="accent" accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.section}>
          {t(errorMessageKey(reserve.error))}
        </AppText>
      )}
    </Sheet>
  );
}

const useStyles = makeStyles((t) => ({
  section: { marginTop: t.spacing.lg },
  single: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm, minHeight: 44 },
  days: { gap: t.spacing.sm },
}));
