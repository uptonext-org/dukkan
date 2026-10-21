import { useState } from 'react';
import { Pressable } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../../../shared/format';
import type { MarketSummary } from '../../../shared/api';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Icon } from '../../../shared/ui';
import { useAuthGate } from '../../auth/AuthGate';
import { useCart } from '../../cart/api/hooks';
import { reservationLabel } from '../format';
import { FulfilmentSheet } from './FulfilmentSheet';

/** Green "Lieferzeit wählen ›" / "Abholzeit wählen ›" link, or the reserved slot. */
export function FulfilmentLink({ market }: { market: MarketSummary }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { requireAuth } = useAuthGate();
  const { data: cart } = useCart(market.id);
  const [open, setOpen] = useState(false);
  const reservation = cart?.fulfilment ?? null;
  const label =
    reservation !== null
      ? reservationLabel(t, reservation)
      : market.fulfilment.delivery
        ? t('fulfilment.chooseDelivery')
        : t('fulfilment.choosePickup');
  return (
    <>
      <Pressable
        onPress={() => requireAuth('checkout', () => setOpen(true))}
        accessibilityRole="button"
        accessibilityLabel={forSpeech(label)}
        accessibilityHint={t('fulfilment.linkHint')}
        style={({ pressed }) => [styles.link, pressed && styles.pressed]}
        testID="fulfilment-link">
        <AppText variant="headline" color="primary" dense numberOfLines={1} style={styles.shrink}>
          {label}
        </AppText>
        <Icon name="chevron" size={18} color={colors.primary} />
      </Pressable>
      <FulfilmentSheet
        visible={open}
        onClose={() => setOpen(false)}
        market={market}
        current={reservation !== null ? { type: reservation.type, slotId: reservation.slot.id, addressId: reservation.addressId ?? null } : null}
      />
    </>
  );
}

const useStyles = makeStyles(() => ({
  link: { flexDirection: 'row', alignItems: 'center', gap: 2, minHeight: 44, flexShrink: 1 },
  shrink: { flexShrink: 1 },
  pressed: { opacity: 0.6 },
}));
