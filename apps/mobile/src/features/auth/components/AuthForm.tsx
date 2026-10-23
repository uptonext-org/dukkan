import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey, isApiError, knownFieldError, type AccountType, type SocialProvider } from '../../../shared/api';
import { useLanguage } from '../../../shared/i18n';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, SegmentedControl, TextField } from '../../../shared/ui';
import { TermsConsent } from '../../legal/TermsConsent';
import { usePlatform } from '../../legal/usePlatform';
import { useAuth } from '../AuthProvider';
import { ForgotPasswordPanel } from './ForgotPasswordPanel';
import { hasErrors, validateLogin, validateRegister, type FieldErrors, type RegisterValues } from '../validation';

type Mode = 'login' | 'register';

/** Sign-in and registration (customer or merchant) in one form. */
export function AuthForm({ initialMode = 'login', onDone }: { initialMode?: Mode; onDone?: () => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { language } = useLanguage();
  const { login, register, socialProviders, socialSignIn } = useAuth();
  const [mode, setMode] = useState<Mode>(initialMode);
  const [forgot, setForgot] = useState(false);
  const [accountType, setAccountType] = useState<AccountType>('CUSTOMER');
  const [values, setValues] = useState<RegisterValues>({ email: '', password: '', firstName: '', lastName: '' });
  const [errors, setErrors] = useState<FieldErrors<keyof RegisterValues>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const legal = usePlatform().data?.legal;
  const termsVersion = legal?.termsVersion ?? null;
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [termsMissing, setTermsMissing] = useState(false);

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
    const needsTerms = mode === 'register' && termsVersion !== null && !acceptedTerms;
    setTermsMissing(needsTerms);
    if (hasErrors(found) || needsTerms) {
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
          acceptedTermsVersion: acceptedTerms ? termsVersion : null,
        });
      }
      onDone?.();
    } catch (error) {
      setServerError(t(errorMessageKey(error)));
      if (isApiError(error) && error.code === 'TERMS_NOT_ACCEPTED') {
        setAcceptedTerms(false);
        setTermsMissing(true);
      }
      if (isApiError(error) && error.code === 'EMAIL_TAKEN') {
        setErrors((e) => ({ ...e, email: 'emailTaken' }));
      }
      if (knownFieldError(error)?.code === 'MaxUtf8Bytes') {
        setErrors((e) => ({ ...e, password: 'passwordTooLong' }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const signInWith = async (provider: SocialProvider) => {
    setServerError(null);
    setSubmitting(true);
    try {
      if (await socialSignIn(provider, accountType, language, acceptedTerms ? termsVersion : null)) {
        onDone?.();
      }
    } catch (error) {
      setServerError(t(errorMessageKey(error)));
    } finally {
      setSubmitting(false);
    }
  };

  if (forgot) {
    return <ForgotPasswordPanel initialEmail={values.email} onBack={() => setForgot(false)} />;
  }

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
        {mode === 'register' && termsVersion !== null && (
          <TermsConsent
            accepted={acceptedTerms}
            onChange={(next) => {
              setAcceptedTerms(next);
              setTermsMissing(false);
            }}
            termsUrl={legal?.termsUrl ?? null}
            privacyUrl={legal?.privacyUrl ?? null}
            error={termsMissing}
          />
        )}
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
        {mode === 'login' && (
          <Button title={t('auth.forgotPassword')} variant="ghost" onPress={() => setForgot(true)} style={styles.forgot} testID="auth-forgot" />
        )}
        {socialProviders.length > 0 && (
          <View style={styles.social}>
            <AppText variant="subhead" color="textSecondary" style={styles.or}>
              {t('auth.orDivider')}
            </AppText>
            {socialProviders.map((provider) => (
              <Button
                key={provider}
                title={t(provider === 'APPLE' ? 'auth.continueWithApple' : 'auth.continueWithGoogle')}
                variant="secondary"
                {...(provider === 'APPLE' ? { icon: 'apple' as const } : {})}
                disabled={submitting}
                onPress={() => void signInWith(provider)}
                testID={`auth-social-${provider}`}
              />
            ))}
          </View>
        )}
      </View>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  fields: { marginTop: t.spacing.lg },
  label: { marginBottom: t.spacing.xs },
  gap: { height: t.spacing.lg },
  serverError: { marginBottom: t.spacing.md },
  forgot: { marginTop: t.spacing.sm, alignSelf: 'center' },
  social: { marginTop: t.spacing.lg, gap: t.spacing.sm },
  or: { textAlign: 'center' },
}));
