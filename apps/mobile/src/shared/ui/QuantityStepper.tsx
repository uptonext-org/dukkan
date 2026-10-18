import { memo } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, radii, spacing, touchTarget, typography } from '../theme';

interface QuantityStepperProps {
  value: number;
  onChange: (next: number) => void;
  min?: number;
  max: number;
  /** Used in accessibility labels, e.g. "Increase quantity of Oat milk". */
  itemName: string;
  disabled?: boolean;
  busy?: boolean;
}

export const QuantityStepper = memo(function QuantityStepper({
  value,
  onChange,
  min = 0,
  max,
  itemName,
  disabled = false,
  busy = false,
}: QuantityStepperProps) {
  const canDecrement = !disabled && value > min;
  const canIncrement = !disabled && value < max;
  return (
    <View style={styles.container} accessible={false} accessibilityLabel={`Quantity of ${itemName}`}>
      <StepButton
        symbol="−"
        label={value - 1 <= 0 && min === 0 ? `Remove ${itemName}` : `Decrease quantity of ${itemName}`}
        enabled={canDecrement}
        onPress={() => onChange(Math.max(min, value - 1))}
      />
      <View style={styles.valueBox}>
        {busy ? (
          <ActivityIndicator color={colors.primary} accessibilityLabel="Updating quantity" />
        ) : (
          <Text
            style={styles.value}
            accessibilityLabel={`Quantity ${value}`}
            accessibilityLiveRegion="polite">
            {value}
          </Text>
        )}
      </View>
      <StepButton
        symbol="+"
        label={`Increase quantity of ${itemName}`}
        hint={value >= max ? `Maximum of ${max} reached` : undefined}
        enabled={canIncrement}
        onPress={() => onChange(Math.min(max, value + 1))}
      />
    </View>
  );
});

interface StepButtonProps {
  symbol: string;
  label: string;
  hint?: string | undefined;
  enabled: boolean;
  onPress: () => void;
}

function StepButton({ symbol, label, hint, enabled, onPress }: StepButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={!enabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      accessibilityState={{ disabled: !enabled }}
      style={({ pressed }) => [styles.button, pressed && styles.pressed, !enabled && styles.disabled]}>
      <Text style={[styles.symbol, !enabled && styles.symbolDisabled]}>{symbol}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.primary,
    backgroundColor: colors.surface,
    alignSelf: 'flex-start',
  },
  button: {
    minWidth: touchTarget,
    minHeight: touchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: { backgroundColor: colors.primaryMuted },
  disabled: { opacity: 0.4 },
  symbol: { fontSize: 22, fontWeight: '700', color: colors.primary },
  symbolDisabled: { color: colors.textMuted },
  valueBox: { minWidth: 36, alignItems: 'center', paddingHorizontal: spacing.xs },
  value: { ...typography.bodyStrong, color: colors.text },
});
