import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, spacing, touchTarget, typography } from '../theme';

interface ListRowProps {
  title: string;
  subtitle?: string | undefined;
  right?: ReactNode;
  onPress?: (() => void) | undefined;
  accessibilityHint?: string;
  disabled?: boolean;
  indent?: number;
}

export function ListRow({
  title,
  subtitle,
  right,
  onPress,
  accessibilityHint,
  disabled = false,
  indent = 0,
}: ListRowProps) {
  const content = (
    <>
      <View style={styles.texts}>
        <Text style={styles.title}>{title}</Text>
        {subtitle !== undefined && <Text style={styles.subtitle}>{subtitle}</Text>}
      </View>
      {right}
    </>
  );
  if (onPress === undefined) {
    return <View style={[styles.row, { paddingLeft: spacing.lg + indent }]}>{content}</View>;
  }
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled }}
      style={({ pressed }) => [
        styles.row,
        { paddingLeft: spacing.lg + indent },
        pressed && styles.pressed,
        disabled && styles.disabled,
      ]}>
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: touchTarget + 12,
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.md,
    paddingRight: spacing.lg,
    backgroundColor: colors.surface,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  pressed: { backgroundColor: colors.primaryMuted },
  disabled: { opacity: 0.55 },
  texts: { flex: 1 },
  title: { ...typography.body, color: colors.text },
  subtitle: { ...typography.caption, color: colors.textMuted, marginTop: 2 },
});
