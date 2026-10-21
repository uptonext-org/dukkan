import { View } from 'react-native';

import { makeStyles } from '../theme';
import { AppText } from './AppText';

/** Yellow "20 % Rabatt" pill on product tiles (promo token, text #2B2B2B, 10.1:1). */
export function PromoPill({ label }: { label: string }) {
  const styles = useStyles();
  return (
    <View style={styles.promo}>
      <AppText variant="caption" color="onPromo" dense numberOfLines={1}>
        {label}
      </AppText>
    </View>
  );
}

/** Dark-green discount label in cart rows (white on primaryStrong, 8.8:1). */
export function DiscountLabel({ label }: { label: string }) {
  const styles = useStyles();
  return (
    <View style={styles.discount}>
      <AppText variant="caption" dense style={styles.onDark} numberOfLines={1}>
        {label}
      </AppText>
    </View>
  );
}

/** Red count badge, e.g. "3 Angebote" on market cards. */
export function AccentBadge({ label }: { label: string }) {
  const styles = useStyles();
  return (
    <View style={styles.accent}>
      <AppText variant="caption" dense style={styles.onDark} numberOfLines={1}>
        {label}
      </AppText>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  promo: {
    backgroundColor: t.colors.promo,
    borderRadius: t.radii.pill,
    paddingHorizontal: t.spacing.sm,
    paddingVertical: 2,
    alignSelf: 'flex-start',
  },
  discount: {
    backgroundColor: t.colors.primaryStrong,
    borderRadius: t.radii.label,
    paddingHorizontal: t.spacing.sm,
    paddingVertical: 2,
    alignSelf: 'flex-start',
  },
  accent: {
    backgroundColor: t.colors.accentFill,
    borderRadius: t.radii.full,
    paddingHorizontal: t.spacing.sm,
    paddingVertical: 2,
    alignSelf: 'flex-start',
  },
  onDark: { color: t.colors.onAccent },
}));
