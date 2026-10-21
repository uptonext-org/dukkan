import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { useIsOffline } from '../network/useIsOffline';
import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';
import { Icon } from './Icon';

export function OfflineBanner() {
  const offline = useIsOffline();
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  if (!offline) {
    return null;
  }
  return (
    <View style={styles.banner} accessibilityRole="alert" accessibilityLiveRegion="assertive">
      <Icon name="offline" size={16} color={colors.onPromo} />
      <AppText variant="caption" color="onPromo" dense>
        {t('states.offlineBanner')}
      </AppText>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  banner: {
    flexDirection: 'row',
    gap: t.spacing.sm,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.colors.promo,
    paddingVertical: t.spacing.sm,
    paddingHorizontal: t.layout.gutter,
  },
}));
