import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';
import { Icon, type IconName } from './Icon';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';

interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  icon?: IconName;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  style?: StyleProp<ViewStyle>;
  testID?: string;
  trailing?: ReactNode;
}

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled = false,
  loading = false,
  icon,
  accessibilityLabel,
  accessibilityHint,
  style,
  testID,
  trailing,
}: ButtonProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const inactive = disabled || loading;
  const palette = {
    primary: { bg: colors.accentFill, fg: colors.onAccent, border: colors.accentFill },
    secondary: { bg: colors.surfaceMuted, fg: colors.text, border: colors.surfaceMuted },
    outline: { bg: 'transparent', fg: colors.primary, border: colors.primary },
    ghost: { bg: 'transparent', fg: colors.primary, border: 'transparent' },
    danger: { bg: 'transparent', fg: colors.accent, border: colors.accent },
  }[variant];
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: inactive, busy: loading }}
      style={({ pressed }) => [
        styles.base,
        { backgroundColor: palette.bg, borderColor: palette.border },
        pressed && styles.pressed,
        inactive && styles.inactive,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={palette.fg} />
      ) : (
        <View style={styles.row}>
          {icon !== undefined && <Icon name={icon} size={20} color={palette.fg} />}
          <AppText variant="bodyStrong" style={{ color: palette.fg }} center numberOfLines={2}>
            {title}
          </AppText>
          {trailing}
        </View>
      )}
    </Pressable>
  );
}

const useStyles = makeStyles((t) => ({
  base: {
    minHeight: 52,
    paddingHorizontal: t.spacing.lg,
    paddingVertical: t.spacing.sm,
    borderRadius: t.radii.pill,
    borderWidth: StyleSheet.hairlineWidth * 3,
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
  pressed: { opacity: 0.85 },
  inactive: { opacity: 0.45 },
}));

interface IconButtonProps {
  icon: IconName;
  label: string;
  onPress: () => void;
  hint?: string;
  color?: string;
  filled?: boolean;
  size?: number;
  selected?: boolean;
  disabled?: boolean;
  testID?: string;
  style?: StyleProp<ViewStyle>;
}

/** Icon-only control with a 44 × 44 pt target and a spoken label (accessibility.md). */
export function IconButton({
  icon,
  label,
  onPress,
  hint,
  color,
  filled,
  size = 24,
  selected,
  disabled = false,
  testID,
  style,
}: IconButtonProps) {
  const { touchTarget } = useTheme();
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      accessibilityState={{ disabled, ...(selected !== undefined ? { selected } : {}) }}
      hitSlop={4}
      style={({ pressed }) => [
        { minWidth: touchTarget, minHeight: touchTarget, alignItems: 'center', justifyContent: 'center' },
        pressed && { opacity: 0.6 },
        disabled && { opacity: 0.4 },
        style,
      ]}>
      <Icon name={icon} size={size} {...(color !== undefined ? { color } : {})} filled={filled ?? false} />
    </Pressable>
  );
}
