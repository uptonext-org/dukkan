import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, knownFieldError, useApi } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader, TextField } from '../../../shared/ui';
import { validateNewPassword } from '../validation';

/** Profil → "Passwort ändern". Other devices are signed out by the server. */
export function ChangePasswordScreen({ navigation }: RootStackScreenProps<'ChangePassword'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const api = useApi();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [errors, setErrors] = useState<{ current?: string; next?: string }>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  const save = async () => {
    const nextKey = validateNewPassword(next);
    const found = {
      ...(current === '' ? { current: t('validation.passwordRequired') } : {}),
      ...(nextKey !== undefined ? { next: t(`validation.${nextKey}`) } : {}),
    };
    setErrors(found);
    setServerError(null);
    if (Object.keys(found).length > 0) {
      return;
    }
    setSaving(true);
    try {
      await api.changePassword(current, next);
      setDone(true);
    } catch (e) {
      const field = knownFieldError(e);
      if (field?.code === 'Incorrect') {
        setErrors({ current: t('errors.field.Incorrect') });
      } else {
        setServerError(t(errorMessageKey(e)));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Screen scroll header={<ScreenHeader title={t('auth.changePassword')} onBack={() => navigation.goBack()} />}>
      {done ? (
        <AppText variant="body" accessibilityRole="alert" style={styles.message} testID="change-done">
          {t('auth.passwordUpdated')}
        </AppText>
      ) : (
        <>
          <TextField
            label={t('auth.currentPassword')}
            value={current}
            onChangeText={setCurrent}
            error={errors.current}
            secureTextEntry
            autoComplete="current-password"
            textContentType="password"
            testID="change-current"
          />
          <TextField
            label={t('auth.newPassword')}
            value={next}
            onChangeText={setNext}
            error={errors.next}
            hint={t('auth.passwordHint')}
            secureTextEntry
            autoComplete="new-password"
            textContentType="newPassword"
            testID="change-next"
          />
          {serverError !== null && (
            <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.message}>
              {serverError}
            </AppText>
          )}
          <Button title={t('auth.setPassword')} onPress={() => void save()} loading={saving} testID="change-submit" />
        </>
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  message: { marginBottom: t.spacing.lg },
}));
