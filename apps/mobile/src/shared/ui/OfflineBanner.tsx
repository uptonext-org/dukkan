import { StyleSheet, Text, View } from 'react-native';

import { useIsOffline } from '../network/useIsOffline';
import { colors, spacing, typography } from '../theme';

export function OfflineBanner() {
  const offline = useIsOffline();
  if (!offline) {
    return null;
  }
  return (
    <View style={styles.banner} accessibilityRole="alert" accessibilityLiveRegion="assertive">
      <Text style={styles.text}>You are offline. Some content may be out of date.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: colors.warningMuted,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
  },
  text: { ...typography.caption, color: colors.warning, fontWeight: '600', textAlign: 'center' },
});
