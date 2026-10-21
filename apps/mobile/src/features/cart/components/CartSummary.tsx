import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { Cart } from '../../../shared/api';
import { formatMoney, formatMoneyForAccessibility } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, ProgressBar } from '../../../shared/ui';
import { minimumOrderProgress } from '../model';

function Row({ label, value, spoken, strong = false, accent = false }: { label: string; value: string; spoken: string; strong?: boolean; accent?: boolean }) {
  const styles = useStyles();
  return (
    <View style={styles.row} accessible accessibilityLabel={`${label}: ${spoken}`}>
      <AppText variant={strong ? 'title' : 'body'} style={styles.flex}>
        {label}
      </AppText>
      <AppText variant={strong ? 'title' : 'body'} color={accent ? 'primary' : 'text'} dense>
        {value}
      </AppText>
    </View>
  );
}

/** Rabatt, Lieferung (Gratis or amount), Total (inkl. MwSt.) and the minimum-order progress. */
export function CartSummary({ cart }: { cart: Cart }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const progress = minimumOrderProgress(cart);
  const pickup = cart.fulfilment?.type === 'PICKUP';
  const feeIsFree = cart.deliveryFee.amountCents === 0;
  return (
    <View style={styles.box} testID="cart-summary">
      {cart.discountTotal.amountCents > 0 && (
        <Row
          label={t('cart.discount')}
          value={`−${formatMoney(cart.discountTotal)}`}
          spoken={t('cart.minus', { amount: formatMoneyForAccessibility(cart.discountTotal) })}
          accent
        />
      )}
      {!pickup && cart.market.fulfilment.delivery && (
        <Row
          label={t('cart.delivery')}
          value={feeIsFree ? t('fulfilment.free') : formatMoney(cart.deliveryFee)}
          spoken={feeIsFree ? t('fulfilment.free') : formatMoneyForAccessibility(cart.deliveryFee)}
        />
      )}
      {pickup && <Row label={t('cart.pickup')} value={t('fulfilment.free')} spoken={t('fulfilment.free')} />}
      <View style={styles.divider} />
      <View style={styles.row} accessible accessibilityLabel={`${t('cart.total')}: ${formatMoneyForAccessibility(cart.total)}`} testID="cart-total">
        <AppText variant="title" style={styles.flex}>
          {t('cart.total')}{' '}
          <AppText variant="subhead" color="textSecondary">
            {t('cart.inclVat')}
          </AppText>
        </AppText>
        <AppText variant="title" dense>
          {formatMoney(cart.total)}
        </AppText>
      </View>
      {!cart.meetsMinimumOrderValue && (
        <View style={styles.minimum}>
          <AppText variant="subhead" color="textSecondary">
            {t('cart.minimumMissing', { missing: formatMoney(progress.remaining), minimum: formatMoney(cart.minimumOrderValue) })}
          </AppText>
          <ProgressBar
            ratio={progress.ratio}
            accessibilityLabel={t('cart.minimumMissing', {
              missing: formatMoneyForAccessibility(progress.remaining),
              minimum: formatMoneyForAccessibility(cart.minimumOrderValue),
            })}
          />
        </View>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  box: { paddingTop: t.spacing.xl, gap: t.spacing.sm },
  row: { flexDirection: 'row', alignItems: 'baseline', gap: t.spacing.md },
  flex: { flex: 1 },
  divider: { height: 1, backgroundColor: t.colors.separator, marginVertical: t.spacing.xs },
  minimum: { gap: t.spacing.sm, marginTop: t.spacing.md },
}));
