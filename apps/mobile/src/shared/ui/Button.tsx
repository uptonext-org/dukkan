import { ActivityIndicator, Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from 'react-native';

import { colors, radii, spacing, touchTarget, typography } from '../theme';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled = false,
  loading = false,
  accessibilityLabel,
  accessibilityHint,
  style,
  testID,
}: ButtonProps) {
  const inactive = disabled || loading;
  const palette = PALETTES[variant];
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: inactive, busy: loading }}
      hitSlop={4}
      style={({ pressed }) => [
        styles.base,
        { backgroundColor: pressed ? palette.pressed : palette.background, borderColor: palette.border },
        inactive && styles.inactive,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={palette.text} />
      ) : (
        <Text style={[styles.label, { color: palette.text }]}>{title}</Text>
      )}
    </Pressable>
  );
}

const PALETTES: Record<ButtonVariant, { background: string; pressed: string; text: string; border: string }> =
  {
    primary: {
      background: colors.primary,
      pressed: colors.primaryPressed,
      text: colors.onPrimary,
      border: colors.primary,
    },
    secondary: {
      background: colors.surface,
      pressed: colors.primaryMuted,
      text: colors.primary,
      border: colors.primary,
    },
    danger: {
      background: colors.surface,
      pressed: colors.dangerMuted,
      text: colors.danger,
      border: colors.danger,
    },
    ghost: {
      background: 'transparent',
      pressed: colors.primaryMuted,
      text: colors.primary,
      border: 'transparent',
    },
  };

const styles = StyleSheet.create({
  base: {
    minHeight: touchTarget + 4,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  inactive: { opacity: 0.5 },
  label: { ...typography.bodyStrong, textAlign: 'center' },
});
