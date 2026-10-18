import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { errorMessage, isApiError } from '../../../shared/api';
import { colors, spacing, typography } from '../../../shared/theme';
import { Button, Screen, TextField } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { useAuth } from '../AuthProvider';
import { hasErrors, validateLogin, type FieldErrors, type LoginValues } from '../validation';

export function LoginScreen({ navigation }: RootStackScreenProps<'Login'>) {
  const { login } = useAuth();
  const [values, setValues] = useState<LoginValues>({ email: '', password: '' });
  const [errors, setErrors] = useState<FieldErrors<keyof LoginValues>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field: keyof LoginValues) => (text: string) => {
    setValues((prev) => ({ ...prev, [field]: text }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const submit = async () => {
    const validation = validateLogin(values);
    setErrors(validation);
    setFormError(null);
    if (hasErrors(validation)) {
      return;
    }
    setSubmitting(true);
    try {
      await login({ email: values.email.trim(), password: values.password });
    } catch (error) {
      if (isApiError(error) && Object.keys(error.fieldErrors).length > 0) {
        setErrors(error.fieldErrors);
      }
      setFormError(errorMessage(error));
      setSubmitting(false);
    }
  };

  return (
    <Screen scroll>
      <Text style={styles.title} accessibilityRole="header">
        Welcome to Dukkan
      </Text>
      <Text style={styles.subtitle}>Sign in to shop groceries for delivery.</Text>
      <TextField
        label="Email"
        value={values.email}
        onChangeText={update('email')}
        error={errors.email}
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
        returnKeyType="next"
      />
      <TextField
        label="Password"
        value={values.password}
        onChangeText={update('password')}
        error={errors.password}
        secureTextEntry
        autoComplete="current-password"
        textContentType="password"
        returnKeyType="go"
        onSubmitEditing={() => void submit()}
      />
      {formError !== null && (
        <Text style={styles.formError} accessibilityRole="alert" accessibilityLiveRegion="polite">
          {formError}
        </Text>
      )}
      <Button title="Sign in" onPress={() => void submit()} loading={submitting} />
      <Button
        title="Create an account"
        variant="ghost"
        onPress={() => navigation.navigate('Register')}
        style={styles.secondary}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { ...typography.title, color: colors.primary, marginTop: spacing.xl },
  subtitle: { ...typography.body, color: colors.textMuted, marginBottom: spacing.xl, marginTop: spacing.xs },
  formError: { ...typography.body, color: colors.danger, marginBottom: spacing.md },
  secondary: { marginTop: spacing.md },
});
