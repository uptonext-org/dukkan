import { memo } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';
import { Icon } from './Icon';

interface QuantityStepperProps {
  value: number;
  onChange: (next: number) => void;
  max: number;
  itemName: string;
  busy?: boolean;
  disabled?: boolean;
  /** `floating`: white pill over a photo tile; `inline`: muted pill in rows/detail. */
  appearance?: 'floating' | 'inline';
}

/** − n + stepper bounded by maxOrderQuantity. 0 removes the item. */
export const QuantityStepper = memo(function QuantityStepper({
  value,
  onChange,
  max,
  itemName,
  busy = false,
  disabled = false,
  appearance = 'inline',
}: QuantityStepperProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const canDecrement = !disabled && !busy && value > 0;
  const canIncrement = !disabled && !busy && value < max;
  return (
    <View style={[styles.container, appearance === 'floating' ? styles.floating : styles.inline]}>
      <StepButton
        icon={value <= 1 ? 'trash' : 'minus'}
        label={value <= 1 ? t('product.remove', { name: itemName }) : t('product.decrease', { name: itemName })}
        enabled={canDecrement}
        onPress={() => onChange(Math.max(0, value - 1))}
      />
      <View style={styles.valueBox}>
        {busy ? (
          <ActivityIndicator color={colors.primary} accessibilityLabel={t('product.updating')} />
        ) : (
          <AppText
            variant="bodyStrong"
            dense
            accessibilityLabel={t('product.quantityValue', { count: value })}
            accessibilityLiveRegion="polite">
            {String(value)}
          </AppText>
        )}
      </View>
      <StepButton
        icon="plus"
        label={t('product.increase', { name: itemName })}
        {...(value >= max ? { hint: t('product.maxReached', { count: max }) } : {})}
        enabled={canIncrement}
        onPress={() => onChange(Math.min(max, value + 1))}
      />
    </View>
  );
});

function StepButton({
  icon,
  label,
  hint,
  enabled,
  onPress,
}: {
  icon: 'plus' | 'minus' | 'trash';
  label: string;
  hint?: string;
  enabled: boolean;
  onPress: () => void;
}) {
  const { colors, touchTarget } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={!enabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      accessibilityState={{ disabled: !enabled }}
      style={({ pressed }) => [
        { minWidth: touchTarget, minHeight: touchTarget, alignItems: 'center', justifyContent: 'center' },
        pressed && { opacity: 0.5 },
        !enabled && { opacity: 0.35 },
      ]}>
      <Icon name={icon} size={20} color={colors.text} />
    </Pressable>
  );
}

const useStyles = makeStyles((t) => ({
  container: { flexDirection: 'row', alignItems: 'center', borderRadius: t.radii.pill },
  floating: {
    backgroundColor: t.colors.surface,
    shadowColor: t.colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 2,
  },
  inline: { backgroundColor: t.colors.surfaceMuted, alignSelf: 'flex-start' },
  valueBox: { minWidth: 28, alignItems: 'center' },
}));
