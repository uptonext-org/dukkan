import { useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { errorMessage, isApiError } from '../../../shared/api';
import { colors, spacing, typography } from '../../../shared/theme';
import { Button, Screen, TextField } from '../../../shared/ui';
import { useAuth } from '../AuthProvider';
import {
  MIN_PASSWORD_LENGTH,
  hasErrors,
  validateRegister,
  type FieldErrors,
  type RegisterValues,
} from '../validation';

export function RegisterScreen() {
  const { register } = useAuth();
  const [values, setValues] = useState<RegisterValues>({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  });
  const [errors, setErrors] = useState<FieldErrors<keyof RegisterValues>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field: keyof RegisterValues) => (text: string) => {
    setValues((prev) => ({ ...prev, [field]: text }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const submit = async () => {
    const validation = validateRegister(values);
    setErrors(validation);
    setFormError(null);
    if (hasErrors(validation)) {
      return;
    }
    setSubmitting(true);
    try {
      await register({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email.trim(),
        password: values.password,
      });
    } catch (error) {
      if (isApiError(error)) {
        setErrors(error.code === 'EMAIL_TAKEN' ? { email: errorMessage(error) } : error.fieldErrors);
      }
      setFormError(errorMessage(error));
      setSubmitting(false);
    }
  };

  return (
    <Screen scroll>
      <Text style={styles.title} accessibilityRole="header">
        Create your account
      </Text>
      <TextField
        label="First name"
        value={values.firstName}
        onChangeText={update('firstName')}
        error={errors.firstName}
        autoComplete="given-name"
        textContentType="givenName"
      />
      <TextField
        label="Last name"
        value={values.lastName}
        onChangeText={update('lastName')}
        error={errors.lastName}
        autoComplete="family-name"
        textContentType="familyName"
      />
      <TextField
        label="Email"
        value={values.email}
        onChangeText={update('email')}
        error={errors.email}
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
      />
      <TextField
        label="Password"
        value={values.password}
        onChangeText={update('password')}
        error={errors.password}
        hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
        secureTextEntry
        autoComplete="new-password"
        textContentType="newPassword"
      />
      {formError !== null && (
        <Text style={styles.formError} accessibilityRole="alert">
          {formError}
        </Text>
      )}
      <Button title="Create account" onPress={() => void submit()} loading={submitting} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { ...typography.title, color: colors.text, marginBottom: spacing.xl },
  formError: { ...typography.body, color: colors.danger, marginBottom: spacing.md },
});
