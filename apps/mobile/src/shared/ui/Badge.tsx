import { StyleSheet, Text, View } from 'react-native';

import { colors, radii, spacing, typography } from '../theme';

export type BadgeTone = 'promo' | 'neutral' | 'success' | 'warning' | 'danger';

interface BadgeProps {
  label: string;
  tone?: BadgeTone;
  accessibilityLabel?: string;
}

const TONES: Record<BadgeTone, { bg: string; fg: string }> = {
  promo: { bg: colors.promo, fg: colors.onPromo },
  neutral: { bg: colors.surfaceAlt, fg: colors.text },
  success: { bg: colors.primaryMuted, fg: colors.primary },
  warning: { bg: colors.warningMuted, fg: colors.warning },
  danger: { bg: colors.dangerMuted, fg: colors.danger },
};

export function Badge({ label, tone = 'neutral', accessibilityLabel }: BadgeProps) {
  const { bg, fg } = TONES[tone];
  return (
    <View
      style={[styles.badge, { backgroundColor: bg }]}
      accessible
      accessibilityLabel={accessibilityLabel ?? label}>
      <Text style={[styles.text, { color: fg }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    borderRadius: radii.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
  },
  text: { ...typography.caption, fontWeight: '700' },
});
