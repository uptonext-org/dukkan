import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey, isApiError, type AccountType } from '../../../shared/api';
import { useLanguage } from '../../../shared/i18n';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, SegmentedControl, TextField } from '../../../shared/ui';
import { useAuth } from '../AuthProvider';
import { hasErrors, validateLogin, validateRegister, type FieldErrors, type RegisterValues } from '../validation';

type Mode = 'login' | 'register';

/** Sign-in and registration (customer or merchant) in one form. */
export function AuthForm({ initialMode = 'login', onDone }: { initialMode?: Mode; onDone?: () => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { language } = useLanguage();
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>(initialMode);
  const [accountType, setAccountType] = useState<AccountType>('CUSTOMER');
  const [values, setValues] = useState<RegisterValues>({ email: '', password: '', firstName: '', lastName: '' });
  const [errors, setErrors] = useState<FieldErrors<keyof RegisterValues>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field: keyof RegisterValues) => (text: string) => {
    setValues((v) => ({ ...v, [field]: text }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };
  const fieldError = (field: keyof RegisterValues) => {
    const key = errors[field];
    return key !== undefined ? t(`validation.${key}`) : undefined;
  };

  const submit = async () => {
    const found = mode === 'login' ? validateLogin(values) : validateRegister(values);
    setErrors(found);
    setServerError(null);
    if (hasErrors(found)) {
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login({ email: values.email.trim(), password: values.password });
      } else {
        await register({
          email: values.email.trim(),
          password: values.password,
          firstName: values.firstName.trim(),
          lastName: values.lastName.trim(),
          accountType,
          preferredLocale: language,
        });
      }
      onDone?.();
    } catch (error) {
      setServerError(t(errorMessageKey(error)));
      if (isApiError(error) && error.code === 'EMAIL_TAKEN') {
        setErrors((e) => ({ ...e, email: 'emailTaken' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <View>
      <SegmentedControl
        accessibilityLabel={t('auth.modeLabel')}
        segments={[
          { value: 'login', label: t('auth.signIn') },
          { value: 'register', label: t('auth.register') },
        ]}
        value={mode}
        onChange={(next) => {
          setMode(next);
          setErrors({});
          setServerError(null);
        }}
      />
      <View style={styles.fields}>
        {mode === 'register' && (
          <>
            <AppText variant="subhead" color="textSecondary" style={styles.label}>
              {t('auth.accountType')}
            </AppText>
            <SegmentedControl
              accessibilityLabel={t('auth.accountType')}
              segments={[
                { value: 'CUSTOMER', label: t('auth.customer') },
                { value: 'MERCHANT', label: t('auth.merchant') },
              ]}
              value={accountType}
              onChange={setAccountType}
            />
            <View style={styles.gap} />
            <TextField label={t('auth.firstName')} value={values.firstName} onChangeText={update('firstName')} error={fieldError('firstName')} autoComplete="given-name" textContentType="givenName" />
            <TextField label={t('auth.lastName')} value={values.lastName} onChangeText={update('lastName')} error={fieldError('lastName')} autoComplete="family-name" textContentType="familyName" />
          </>
        )}
        <TextField
          label={t('auth.email')}
          value={values.email}
          onChangeText={update('email')}
          error={fieldError('email')}
          keyboardType="email-address"
          autoCapitalize="none"
          autoComplete="email"
          textContentType="emailAddress"
        />
        <TextField
          label={t('auth.password')}
          value={values.password}
          onChangeText={update('password')}
          error={fieldError('password')}
          secureTextEntry
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          textContentType={mode === 'login' ? 'password' : 'newPassword'}
          {...(mode === 'register' ? { hint: t('auth.passwordHint') } : {})}
        />
        {serverError !== null && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.serverError}>
            {serverError}
          </AppText>
        )}
        <Button
          title={mode === 'login' ? t('auth.signIn') : t('auth.createAccount')}
          onPress={() => void submit()}
          loading={submitting}
          testID="auth-submit"
        />
      </View>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  fields: { marginTop: t.spacing.lg },
  label: { marginBottom: t.spacing.xs },
  gap: { height: t.spacing.lg },
  serverError: { marginBottom: t.spacing.md },
}));
