import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { Cart } from '../../../shared/api';
import { formatMoney, formatMoneyForAccessibility, formatMoneyShort, forSpeech } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, ProgressBar } from '../../../shared/ui';
import { feeLines, minimumOrderProgress, type FeeLine } from '../model';

function Row({
  label,
  value,
  spoken,
  note,
  strong = false,
  accent = false,
  testID,
}: {
  label: string;
  value: string;
  spoken: string;
  note?: string;
  strong?: boolean;
  accent?: boolean;
  testID?: string;
}) {
  const styles = useStyles();
  return (
    <View accessible accessibilityLabel={forSpeech([`${label}: ${spoken}`, note].filter(Boolean).join(', '))} testID={testID}>
      <View style={styles.row}>
        <AppText variant={strong ? 'title' : 'body'} style={styles.flex}>
          {label}
        </AppText>
        <AppText variant={strong ? 'title' : 'body'} color={accent ? 'primary' : 'text'} dense>
          {value}
        </AppText>
      </View>
      {note !== undefined && (
        <AppText variant="subhead" color="textSecondary">
          {note}
        </AppText>
      )}
    </View>
  );
}

/**
 * Rabatt, the delivery/pickup fee and Total (inkl. MwSt.) plus the minimum-order progress.
 * Before a slot is reserved the server's `deliveryFee` is not final, so the market's fee is shown
 * as "ab 2,90 €" and the total says that delivery may be added. Server totals stay authoritative.
 */
export function CartSummary({ cart }: { cart: Cart }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const progress = minimumOrderProgress(cart);
  const lines = feeLines(cart);
  const feeMayBeAdded = lines.some((line) => line.kind === 'deliveryEstimate' && line.fee !== null && line.fee.amountCents > 0);

  const feeRow = (line: FeeLine) => {
    switch (line.kind) {
      case 'deliveryEstimate': {
        if (line.fee === null || line.fee.amountCents === 0) {
          return <Row key="delivery" label={t('cart.delivery')} value={t('fulfilment.free')} spoken={t('fulfilment.free')} testID="cart-fee-delivery" />;
        }
        const note = line.freeFrom !== null ? t('cart.freeDeliveryFrom', { amount: formatMoneyShort(line.freeFrom) }) : undefined;
        return (
          <Row
            key="delivery"
            label={t('cart.delivery')}
            value={t('cart.feeFrom', { amount: formatMoney(line.fee) })}
            spoken={t('cart.feeFrom', { amount: formatMoneyForAccessibility(line.fee) })}
            {...(note !== undefined ? { note } : {})}
            testID="cart-fee-delivery"
          />
        );
      }
      case 'pickupEstimate':
        return <Row key="pickup" label={t('cart.pickup')} value={t('cart.pickupFree')} spoken={t('cart.pickupFree')} testID="cart-fee-pickup" />;
      case 'delivery': {
        const free = line.fee.amountCents === 0;
        return (
          <Row
            key="delivery"
            label={t('cart.delivery')}
            value={free ? t('fulfilment.free') : formatMoney(line.fee)}
            spoken={free ? t('fulfilment.free') : formatMoneyForAccessibility(line.fee)}
            testID="cart-fee-delivery"
          />
        );
      }
      case 'pickup': {
        const free = line.fee.amountCents === 0;
        return (
          <Row
            key="pickup"
            label={t('cart.pickup')}
            value={free ? t('cart.pickupFree') : formatMoney(line.fee)}
            spoken={free ? t('cart.pickupFree') : formatMoneyForAccessibility(line.fee)}
            testID="cart-fee-pickup"
          />
        );
      }
    }
  };

  const vatNote = feeMayBeAdded ? t('cart.inclVatPlusDelivery') : t('cart.inclVat');
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
      {lines.map(feeRow)}
      <View style={styles.divider} />
      <View style={styles.row} accessible accessibilityLabel={forSpeech(`${t('cart.total')} ${vatNote}: ${formatMoneyForAccessibility(cart.total)}`)} testID="cart-total">
        <AppText variant="title" style={styles.flex}>
          {t('cart.total')}{' '}
          <AppText variant="subhead" color="textSecondary">
            {vatNote}
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
