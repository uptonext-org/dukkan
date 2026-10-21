import { Pressable } from 'react-native';

import { makeStyles } from '../theme';
import { AppText } from './AppText';
import { Icon, type IconName } from './Icon';

interface ChipProps {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  icon?: IconName;
  accessibilityHint?: string;
  disabled?: boolean;
  testID?: string;
}

/** Filter chip (toggle) or static label chip when `onPress` is absent. */
export function Chip({ label, selected = false, onPress, icon, accessibilityHint, disabled = false, testID }: ChipProps) {
  const styles = useStyles();
  const interactive = onPress !== undefined;
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={!interactive || disabled}
      accessibilityRole={interactive ? 'button' : 'text'}
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={interactive ? { selected, disabled } : undefined}
      style={({ pressed }) => [
        styles.chip,
        interactive && styles.interactive,
        selected && styles.selected,
        pressed && styles.pressed,
        disabled && styles.disabled,
      ]}>
      {icon !== undefined && <Icon name={icon} size={16} color={selected ? styles.selectedText.color : styles.text.color} />}
      <AppText variant="caption" dense style={selected ? styles.selectedText : styles.text} numberOfLines={1}>
        {label}
      </AppText>
    </Pressable>
  );
}

const useStyles = makeStyles((t) => ({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: t.spacing.xs,
    borderRadius: t.radii.full,
    paddingHorizontal: t.spacing.md,
    paddingVertical: t.spacing.xs,
    backgroundColor: t.colors.surfaceMuted,
    alignSelf: 'flex-start',
  },
  interactive: { minHeight: 44, paddingHorizontal: t.spacing.lg },
  selected: { backgroundColor: t.colors.primaryStrong },
  pressed: { opacity: 0.75 },
  disabled: { opacity: 0.4 },
  text: { color: t.colors.text },
  selectedText: { color: t.colors.onAccent },
}));
