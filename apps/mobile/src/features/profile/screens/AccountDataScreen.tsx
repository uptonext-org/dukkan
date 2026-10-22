import { useState } from 'react';
import { Share, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, knownFieldError, useApi } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader, TextField } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';

/**
 * Profil → "Meine Daten & Konto" (B-51): export everything stored about the user, and delete the
 * account (GDPR; required by the App Store and Google Play). Deleting asks twice.
 */
export function AccountDataScreen({ navigation }: RootStackScreenProps<'AccountData'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const api = useApi();
  const { logout } = useAuth();
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [passwordError, setPasswordError] = useState<string | undefined>(undefined);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const exportData = async () => {
    setExporting(true);
    setExportError(null);
    try {
      const data = await api.exportMyData();
      await Share.share({ title: t('account.exportShareTitle'), message: JSON.stringify(data, null, 2) });
    } catch (e) {
      setExportError(t(errorMessageKey(e)));
    } finally {
      setExporting(false);
    }
  };

  const deleteAccount = async () => {
    setDeleting(true);
    setPasswordError(undefined);
    setDeleteError(null);
    try {
      await api.deleteMyAccount(password === '' ? null : password);
      // The server has ended every session; this clears the local one and the device registration.
      await logout();
      navigation.popToTop();
    } catch (e) {
      setConfirming(false);
      if (knownFieldError(e)?.code === 'Incorrect') {
        setPasswordError(t('errors.field.Incorrect'));
      } else {
        setDeleteError(t(errorMessageKey(e)));
      }
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Screen scroll header={<ScreenHeader title={t('account.title')} onBack={() => navigation.goBack()} />}>
      <View style={styles.section}>
        <AppText variant="headline">{t('account.exportTitle')}</AppText>
        <AppText variant="body" color="textSecondary">
          {t('account.exportText')}
        </AppText>
        <Button title={t('account.exportButton')} variant="secondary" icon="download" onPress={() => void exportData()} loading={exporting} testID="account-export" />
        {exportError !== null && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert">
            {exportError}
          </AppText>
        )}
      </View>

      <View style={styles.section}>
        <AppText variant="headline">{t('account.deleteTitle')}</AppText>
        <AppText variant="body" color="textSecondary">
          {t('account.deleteText')}
        </AppText>
        <TextField
          label={t('auth.password')}
          value={password}
          onChangeText={setPassword}
          error={passwordError}
          hint={t('account.passwordHint')}
          secureTextEntry
          autoComplete="current-password"
          textContentType="password"
          testID="account-delete-password"
        />
        {deleteError !== null && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert" testID="account-delete-error">
            {deleteError}
          </AppText>
        )}
        {confirming ? (
          <View style={styles.confirm} accessibilityLiveRegion="assertive">
            <AppText variant="bodyStrong" color="accent" accessibilityRole="alert">
              {t('account.confirmText')}
            </AppText>
            <Button title={t('account.confirmButton')} variant="danger" icon="trash" onPress={() => void deleteAccount()} loading={deleting} testID="account-delete-confirm" />
            <Button title={t('account.cancel')} variant="ghost" onPress={() => setConfirming(false)} disabled={deleting} />
          </View>
        ) : (
          <Button title={t('account.deleteButton')} variant="danger" onPress={() => setConfirming(true)} testID="account-delete" />
        )}
      </View>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  section: { gap: t.spacing.md, marginBottom: t.spacing.xxl },
  confirm: { gap: t.spacing.sm },
}));
