import { TextInput, View, type TextInputProps } from 'react-native';

import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';

export interface TextFieldProps extends Omit<TextInputProps, 'style'> {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
  /** Writing direction of the typed content when it differs from the UI (e.g. an Arabic name field in German UI). */
  inputDirection?: 'ltr' | 'rtl';
}

export function TextField({ label, error, hint, accessibilityHint, inputDirection, ...inputProps }: TextFieldProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const hasError = error !== undefined && error !== '';
  return (
    <View style={styles.container}>
      <AppText variant="subhead" color="textSecondary" style={styles.label}>
        {label}
      </AppText>
      <View style={inputDirection !== undefined ? { direction: inputDirection } : undefined}>
        <TextInput
          accessibilityLabel={label}
          accessibilityHint={hasError ? error : (accessibilityHint ?? hint)}
          placeholderTextColor={colors.textPlaceholder}
          style={[styles.input, hasError && styles.inputError]}
          {...inputProps}
        />
      </View>
      {hasError ? (
        <AppText variant="caption" color="accent" style={styles.below} accessibilityLiveRegion="polite" accessibilityRole="alert">
          {error}
        </AppText>
      ) : (
        hint !== undefined && (
          <AppText variant="caption" color="textSecondary" style={styles.below}>
            {hint}
          </AppText>
        )
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  container: { marginBottom: t.spacing.lg },
  label: { marginBottom: t.spacing.xs },
  input: {
    ...t.type.body,
    minHeight: 48,
    borderRadius: t.radii.pill,
    paddingHorizontal: t.spacing.md,
    paddingVertical: t.spacing.sm,
    backgroundColor: t.colors.surfaceMuted,
    color: t.colors.text,
    textAlign: 'auto' as const,
  },
  inputError: { borderWidth: 1.5, borderColor: t.colors.accent },
  below: { marginTop: t.spacing.xs },
}));
