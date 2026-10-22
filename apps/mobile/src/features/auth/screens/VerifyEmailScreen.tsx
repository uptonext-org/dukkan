import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, knownFieldError, queryKeys, useApi } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, Screen, ScreenHeader } from '../../../shared/ui';

/** Opened by the link in the welcome mail (`dukkan://verify-email?token=…`, B-39b). */
export function VerifyEmailScreen({ route, navigation }: RootStackScreenProps<'VerifyEmail'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const api = useApi();
  const queryClient = useQueryClient();
  const verify = useMutation({
    mutationFn: (token: string) => api.verifyEmail(token),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.me }),
  });
  const { mutate } = verify;
  const token = route.params.token;

  useEffect(() => {
    mutate(token);
  }, [mutate, token]);

  const leave = () => {
    if (navigation.canGoBack()) {
      navigation.goBack();
    } else {
      navigation.reset({ index: 0, routes: [{ name: navigation.getState().routeNames.includes('Shell') ? 'Shell' : 'Welcome' }] });
    }
  };
  const fieldCode = verify.isError ? knownFieldError(verify.error)?.code : undefined;

  return (
    <Screen scroll header={<ScreenHeader title={t('verifyEmail.title')} onBack={leave} backIcon="close" />}>
      {verify.isPending && <AppText variant="body">{t('verifyEmail.working')}</AppText>}
      {verify.isSuccess && (
        <AppText variant="body" accessibilityRole="alert" style={styles.message} testID="verify-done">
          {t('verifyEmail.done')}
        </AppText>
      )}
      {verify.isError && (
        <AppText variant="body" color="accent" accessibilityRole="alert" style={styles.message} testID="verify-error">
          {fieldCode !== undefined ? t(`errors.field.${fieldCode}`) : t(errorMessageKey(verify.error))}
        </AppText>
      )}
      {!verify.isPending && <Button title={t('verifyEmail.continue')} onPress={leave} testID="verify-continue" />}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  message: { marginBottom: t.spacing.lg },
}));
