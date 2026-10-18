import { Pressable, StyleSheet, Text } from 'react-native';

import { colors, radii, spacing, touchTarget, typography } from '../theme';

interface ChipProps {
  label: string;
  selected: boolean;
  onPress: () => void;
  accessibilityRole?: 'radio' | 'button' | 'switch';
}

export function Chip({ label, selected, onPress, accessibilityRole = 'radio' }: ChipProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole={accessibilityRole}
      accessibilityLabel={label}
      accessibilityState={accessibilityRole === 'button' ? { selected } : { checked: selected }}
      style={[styles.chip, selected && styles.selected]}>
      <Text style={[styles.text, selected && styles.textSelected]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: touchTarget,
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    marginRight: spacing.sm,
  },
  selected: { backgroundColor: colors.primary, borderColor: colors.primary },
  text: { ...typography.body, color: colors.text },
  textSelected: { color: colors.onPrimary, fontWeight: '600' },
});
