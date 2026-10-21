import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { Order } from '../../../shared/api';
import { formatMoney, formatMoneyForAccessibility, forSpeech } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText } from '../../../shared/ui';

/** Lines and totals of a placed order (server snapshot). */
export function OrderSummary({ order }: { order: Order }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const rows: [string, string, string][] = [
    [t('order.subtotal'), formatMoney(order.subtotal), formatMoneyForAccessibility(order.subtotal)],
    ...(order.discountTotal.amountCents > 0
      ? [[t('cart.discount'), `−${formatMoney(order.discountTotal)}`, t('cart.minus', { amount: formatMoneyForAccessibility(order.discountTotal) })] as [string, string, string]]
      : []),
    ...(order.fulfilmentType === 'DELIVERY'
      ? [[t('cart.delivery'), order.deliveryFee.amountCents === 0 ? t('fulfilment.free') : formatMoney(order.deliveryFee), formatMoneyForAccessibility(order.deliveryFee)] as [string, string, string]]
      : []),
  ];
  return (
    <View style={styles.box}>
      {order.items.map((item) => (
        <View key={item.productId} style={styles.row} accessible accessibilityLabel={forSpeech(`${item.quantity} × ${item.name}, ${formatMoneyForAccessibility(item.lineTotal)}`)}>
          <AppText variant="body" style={styles.flex}>
            {`${item.quantity} × ${item.name}`}
          </AppText>
          <AppText variant="body" dense>
            {formatMoney(item.lineTotal)}
          </AppText>
        </View>
      ))}
      <View style={styles.divider} />
      {rows.map(([label, value, spoken]) => (
        <View key={label} style={styles.row} accessible accessibilityLabel={forSpeech(`${label}: ${spoken}`)}>
          <AppText variant="body" color="textSecondary" style={styles.flex}>
            {label}
          </AppText>
          <AppText variant="body" dense>
            {value}
          </AppText>
        </View>
      ))}
      <View style={styles.row} accessible accessibilityLabel={`${t('cart.total')}: ${formatMoneyForAccessibility(order.total)}`}>
        <AppText variant="headline" style={styles.flex}>
          {t('cart.total')}
        </AppText>
        <AppText variant="headline" dense>
          {formatMoney(order.total)}
        </AppText>
      </View>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  box: { gap: t.spacing.sm },
  row: { flexDirection: 'row', gap: t.spacing.md, alignItems: 'baseline' },
  flex: { flex: 1 },
  divider: { height: 1, backgroundColor: t.colors.separator, marginVertical: t.spacing.xs },
}));
