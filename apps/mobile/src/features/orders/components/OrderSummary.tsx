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
  const short = order.items.filter((item) => item.fulfilledQuantity != null);
  return (
    <View style={styles.box}>
      {short.length > 0 && (
        <View style={styles.notice} testID="order-adjusted" accessibilityRole="summary">
          <AppText variant="bodyStrong">{t('order.adjustedTitle')}</AppText>
          <AppText variant="subhead" color="textSecondary">
            {t('order.adjustedMessage', { count: short.length })}
          </AppText>
        </View>
      )}
      {order.items.map((item) => {
        const missing = item.fulfilledQuantity === 0;
        const label =
          item.fulfilledQuantity == null
            ? `${item.quantity} × ${item.name}`
            : missing
              ? `${item.quantity} × ${item.name} · ${t('order.missing')}`
              : `${item.fulfilledQuantity} × ${item.name} · ${t('order.ofOrdered', { count: item.quantity })}`;
        return (
          <View
            key={item.lineNo}
            style={styles.row}
            accessible
            accessibilityLabel={forSpeech(`${label}, ${formatMoneyForAccessibility(item.lineTotal)}`)}
            testID={`order-line-${item.lineNo}`}>
            <AppText variant="body" color={missing ? 'textSecondary' : 'text'} style={[styles.flex, missing && styles.struck]}>
              {label}
            </AppText>
            <AppText variant="body" dense color={missing ? 'textSecondary' : 'text'}>
              {formatMoney(item.lineTotal)}
            </AppText>
          </View>
        );
      })}
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
      {order.originalTotal != null && (
        <AppText variant="subhead" color="textSecondary" style={styles.end}>
          {t('order.insteadOf', { amount: formatMoney(order.originalTotal) })}
        </AppText>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  box: { gap: t.spacing.sm },
  row: { flexDirection: 'row', gap: t.spacing.md, alignItems: 'baseline' },
  flex: { flex: 1 },
  divider: { height: 1, backgroundColor: t.colors.separator, marginVertical: t.spacing.xs },
  struck: { textDecorationLine: 'line-through' },
  end: { textAlign: 'right' },
  notice: { gap: 2, padding: t.spacing.md, borderRadius: 12, backgroundColor: t.colors.surfaceMuted, marginBottom: t.spacing.xs },
}));
