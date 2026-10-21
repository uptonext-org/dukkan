import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';

import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';
import { Icon, type IconName } from './Icon';

interface ListRowProps {
  title: string;
  subtitle?: string;
  icon?: IconName;
  leading?: ReactNode;
  trailing?: ReactNode;
  onPress?: () => void;
  chevron?: boolean;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  selected?: boolean;
  disabled?: boolean;
  testID?: string;
}

/** Row with optional leading tile, title/subtitle and a direction-aware chevron. */
export function ListRow({
  title,
  subtitle,
  icon,
  leading,
  trailing,
  onPress,
  chevron = onPress !== undefined,
  accessibilityLabel,
  accessibilityHint,
  selected,
  disabled = false,
  testID,
}: ListRowProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const content = (
    <>
      {leading}
      {icon !== undefined && leading === undefined && <Icon name={icon} size={22} color={colors.text} />}
      <View style={styles.texts}>
        <AppText variant="body" dense numberOfLines={2}>
          {title}
        </AppText>
        {subtitle !== undefined && (
          <AppText variant="subhead" color="textSecondary" dense numberOfLines={2}>
            {subtitle}
          </AppText>
        )}
      </View>
      {trailing}
      {chevron && <Icon name="chevron" size={20} color={colors.textSecondary} />}
    </>
  );
  if (onPress === undefined) {
    return (
      <View style={styles.row} testID={testID} accessible accessibilityLabel={accessibilityLabel}>
        {content}
      </View>
    );
  }
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? (subtitle !== undefined ? `${title}, ${subtitle}` : title)}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled, ...(selected !== undefined ? { selected } : {}) }}
      style={({ pressed }) => [styles.row, pressed && styles.pressed, disabled && styles.disabled]}>
      {content}
    </Pressable>
  );
}

const useStyles = makeStyles((t) => ({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: t.spacing.md,
    minHeight: 56,
    paddingVertical: t.spacing.sm,
  },
  texts: { flex: 1, gap: 2 },
  pressed: { opacity: 0.6 },
  disabled: { opacity: 0.4 },
}));

export function Separator() {
  const { colors } = useTheme();
  return <View style={{ height: 1, backgroundColor: colors.separator }} />;
}
