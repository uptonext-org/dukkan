import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey, useApi } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, TextField } from '../../../shared/ui';
import { validateEmail } from '../validation';

/**
 * "Passwort vergessen?": asks for the email and requests a reset link. The answer is the same
 * whether or not an account exists (the server never tells), so the message is phrased as "if".
 */
export function ForgotPasswordPanel({ initialEmail, onBack }: { initialEmail: string; onBack: () => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const api = useApi();
  const [email, setEmail] = useState(initialEmail);
  const [error, setError] = useState<string | undefined>();
  const [serverError, setServerError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  const send = async () => {
    const key = validateEmail(email);
    setError(key !== undefined ? t(`validation.${key}`) : undefined);
    setServerError(null);
    if (key !== undefined) {
      return;
    }
    setSending(true);
    try {
      await api.requestPasswordReset(email.trim());
      setSent(true);
    } catch (e) {
      setServerError(t(errorMessageKey(e)));
    } finally {
      setSending(false);
    }
  };

  return (
    <View testID="forgot-password">
      <AppText variant="headline" accessibilityRole="header">
        {t('auth.forgotTitle')}
      </AppText>
      <AppText variant="subhead" color="textSecondary" style={styles.intro}>
        {t('auth.forgotIntro')}
      </AppText>
      {sent ? (
        <AppText variant="body" accessibilityRole="alert" accessibilityLiveRegion="polite" style={styles.sent} testID="forgot-sent">
          {t('auth.linkSent')}
        </AppText>
      ) : (
        <>
          <TextField
            label={t('auth.email')}
            value={email}
            onChangeText={(text) => {
              setEmail(text);
              setError(undefined);
            }}
            error={error}
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            textContentType="emailAddress"
            testID="forgot-email"
          />
          {serverError !== null && (
            <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.error}>
              {serverError}
            </AppText>
          )}
          <Button title={t('auth.sendLink')} onPress={() => void send()} loading={sending} testID="forgot-submit" />
        </>
      )}
      <Button title={t('auth.backToSignIn')} variant="ghost" onPress={onBack} style={styles.back} testID="forgot-back" />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  intro: { marginTop: t.spacing.xs, marginBottom: t.spacing.lg },
  sent: { marginBottom: t.spacing.lg },
  error: { marginBottom: t.spacing.md },
  back: { marginTop: t.spacing.sm },
}));
