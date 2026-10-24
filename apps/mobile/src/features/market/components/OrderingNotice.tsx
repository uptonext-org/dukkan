import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketSummary } from '../../../shared/api';
import { formatDate, formatDateTime } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText } from '../../../shared/ui';

/**
 * A pause or holiday the merchant set, shown above every tab of the market. Nothing appears for a
 * market that simply takes no online orders (a showcase) or that set neither.
 */
export function OrderingNotice({ market }: { market: MarketSummary | undefined }) {
  const styles = useStyles();
  const { t } = useTranslation();
  if (market === undefined) {
    return null;
  }
  const lines = [
    market.ordersPausedUntil != null ? t('market.ordering.pausedUntil', { time: formatDateTime(market.ordersPausedUntil) }) : null,
    market.closedFrom != null && market.closedUntil != null
      ? t('market.ordering.closed', { from: formatDate(market.closedFrom), until: formatDate(market.closedUntil) })
      : null,
  ].filter((line): line is string => line !== null);
  if (lines.length === 0) {
    return null;
  }
  return (
    <View style={styles.notice} accessibilityRole="alert" testID="ordering-notice">
      {lines.map((line) => (
        <AppText key={line} variant="callout">
          {line}
        </AppText>
      ))}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  notice: {
    marginHorizontal: t.spacing.lg,
    marginBottom: t.spacing.sm,
    padding: t.spacing.md,
    borderRadius: t.radii.tile,
    backgroundColor: t.colors.surfaceRose,
    gap: 2,
  },
}));
