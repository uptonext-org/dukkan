import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketSummary } from '../../../shared/api';
import { formatMoneyShort } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Icon, type IconName } from '../../../shared/ui';
import { minimumOrderLabel, openingLabel } from '../../marketplace/format';

function Line({ icon, text }: { icon: IconName; text: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <Icon name={icon} size={18} color={colors.primary} />
      <AppText variant="callout" style={{ flex: 1 }}>
        {text}
      </AppText>
    </View>
  );
}

/** Market info card on `surfaceRose`: opening hours, delivery/pickup, minimum order. */
export function MarketInfoCard({ market }: { market: MarketSummary }) {
  const styles = useStyles();
  const { t } = useTranslation();
  // "Lieferung (2,90 €) · Abholung": the fee belongs to delivery, not a third option.
  const { deliveryFee } = market.fulfilment;
  const delivery = !market.fulfilment.delivery
    ? null
    : deliveryFee == null || deliveryFee.amountCents === 0
      ? t('market.deliveryFreeShort')
      : t('market.deliveryWithFee', { amount: formatMoneyShort(deliveryFee) });
  const ways = [delivery, market.fulfilment.pickup ? t('market.pickup') : null].filter(Boolean).join(' · ');
  const minimum = minimumOrderLabel(t, market);
  const freeFrom = market.fulfilment.freeDeliveryFrom;
  return (
    <View style={styles.card} accessible accessibilityRole="summary" testID="market-info-card">
      <AppText variant="headline">{market.name}</AppText>
      <AppText variant="subhead" color="textSecondary">
        {market.addressLine}
      </AppText>
      <View style={styles.lines}>
        <Line icon="clock" text={[openingLabel(t, market), market.todayHours != null ? t('market.today', { hours: market.todayHours }) : null].filter(Boolean).join(' · ')} />
        <Line icon={market.fulfilment.delivery ? 'truck' : 'store'} text={ways} />
        {minimum !== undefined && <Line icon="basket" text={minimum} />}
        {freeFrom != null && market.fulfilment.delivery && <Line icon="tag" text={t('market.freeDeliveryFrom', { amount: formatMoneyShort(freeFrom) })} />}
      </View>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  card: { backgroundColor: t.colors.surfaceRose, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: 2 },
  lines: { marginTop: t.spacing.md, gap: t.spacing.sm },
}));
