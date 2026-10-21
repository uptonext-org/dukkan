import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, knownFieldError, useApi } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader, TextField } from '../../../shared/ui';
import { validateNewPassword } from '../validation';

/** Opened by the link in the reset mail (`dukkan://reset-password?token=…`). */
export function ResetPasswordScreen({ route, navigation }: RootStackScreenProps<'ResetPassword'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const api = useApi();
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);
  // Opened from a link there may be nothing to go back to; before onboarding only Welcome exists.
  const leave = () => {
    if (navigation.canGoBack()) {
      navigation.goBack();
    } else {
      navigation.reset({ index: 0, routes: [{ name: navigation.getState().routeNames.includes('Shell') ? 'Shell' : 'Welcome' }] });
    }
  };

  const save = async () => {
    const key = validateNewPassword(password);
    setError(key !== undefined ? t(`validation.${key}`) : undefined);
    setServerError(null);
    if (key !== undefined) {
      return;
    }
    setSaving(true);
    try {
      await api.confirmPasswordReset(route.params.token, password);
      setDone(true);
    } catch (e) {
      const field = knownFieldError(e);
      setServerError(field !== undefined ? t(`errors.field.${field.code}`) : t(errorMessageKey(e)));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Screen scroll header={<ScreenHeader title={t('auth.resetTitle')} onBack={leave} backIcon="close" />}>
      {done ? (
        <>
          <AppText variant="body" accessibilityRole="alert" style={styles.message} testID="reset-done">
            {t('auth.passwordChanged')}
          </AppText>
          <Button title={t('auth.signIn')} onPress={leave} testID="reset-sign-in" />
        </>
      ) : (
        <>
          <TextField
            label={t('auth.newPassword')}
            value={password}
            onChangeText={(text) => {
              setPassword(text);
              setError(undefined);
            }}
            error={error}
            hint={t('auth.passwordHint')}
            secureTextEntry
            autoComplete="new-password"
            textContentType="newPassword"
            testID="reset-password"
          />
          {serverError !== null && (
            <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.message} testID="reset-error">
              {serverError}
            </AppText>
          )}
          <Button title={t('auth.setPassword')} onPress={() => void save()} loading={saving} testID="reset-submit" />
        </>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  message: { marginBottom: t.spacing.lg },
}));
