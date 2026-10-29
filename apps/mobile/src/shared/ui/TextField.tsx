import { TextInput, View, type TextInputProps } from 'react-native';

import { forSpeech } from '../format/bidi';
import { latinDigits } from '../format/money';
import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';

export interface TextFieldProps extends Omit<TextInputProps, 'style'> {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
  /** Writing direction of the typed content when it differs from the UI (e.g. an Arabic name field in German UI). */
  inputDirection?: 'ltr' | 'rtl';
}

const NUMERIC_KEYBOARDS = new Set(['number-pad', 'decimal-pad', 'numeric', 'phone-pad', 'numbers-and-punctuation']);

export function TextField({ label, error, hint, accessibilityHint, inputDirection, ...inputProps }: TextFieldProps) {
  // Arabic keyboards type ٠-٩: number fields store Latin digits, which the validation and the server expect.
  const onChangeText =
    inputProps.onChangeText !== undefined && NUMERIC_KEYBOARDS.has(inputProps.keyboardType ?? '')
      ? (text: string) => inputProps.onChangeText?.(latinDigits(text))
      : inputProps.onChangeText;
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
          accessibilityLabel={forSpeech(label)}
          accessibilityHint={forSpeech(hasError ? error : (accessibilityHint ?? hint))}
          placeholderTextColor={colors.textPlaceholder}
          style={[styles.input, hasError && styles.inputError]}
          {...inputProps}
          {...(onChangeText !== undefined ? { onChangeText } : {})}
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
