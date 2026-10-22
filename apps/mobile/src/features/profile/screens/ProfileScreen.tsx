import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { ShellTabScreenProps } from '../../../navigation/types';
import { errorMessageKey, useApi } from '../../../shared/api';
import { LANGUAGES, useLanguage } from '../../../shared/i18n';
import { useChosenPlace } from '../../../shared/location';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, ListRow, LoadingState } from '../../../shared/ui';
import { useIsMerchant, useMe } from '../../auth/api/useMe';
import { useAuth } from '../../auth/AuthProvider';
import { AuthForm } from '../../auth/components/AuthForm';
import { useNotifications } from '../../notifications/api/hooks';
import { ShellHeader } from '../../marketplace/components/ShellHeader';
import { LanguageSheet } from '../components/LanguageSheet';

/** Profil: sign-in/registration when signed out; account, language, addresses, orders, Mein Markt, logout. */
export function ProfileScreen({ navigation }: ShellTabScreenProps<'Profile'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { status, logout } = useAuth();
  const unread = useNotifications().data?.unreadCount ?? 0;
  const { language } = useLanguage();
  const { place } = useChosenPlace();
  const me = useMe();
  const merchant = useIsMerchant();
  const [languageOpen, setLanguageOpen] = useState(false);
  const languageName = LANGUAGES.find((l) => l.code === language)?.nativeName ?? language;

  const common = (
    <>
      <ListRow title={t('profile.language')} subtitle={languageName} icon="globe" onPress={() => setLanguageOpen(true)} testID="profile-language" />
      <ListRow
        title={t('profile.location')}
        subtitle={place?.label ?? t('shell.chooseLocation')}
        icon="pin"
        onPress={() => navigation.navigate('Onboarding', { change: true })}
      />
    </>
  );

  return (
    <View style={styles.root}>
      <ShellHeader title={t('profile.title')} showAccount={false} />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {status !== 'signedIn' ? (
          <>
            <AppText variant="body" color="textSecondary" style={styles.intro}>
              {t('profile.signedOutIntro')}
            </AppText>
            <AuthForm />
            <View style={styles.section}>{common}</View>
          </>
        ) : me.isPending ? (
          <LoadingState />
        ) : (
          <>
            <View style={styles.account} accessible accessibilityRole="summary">
              <AppText variant="title">{me.data ? `${me.data.firstName} ${me.data.lastName}` : ''}</AppText>
              <AppText variant="subhead" color="textSecondary">
                {me.data?.email ?? ''}
              </AppText>
              {merchant && (
                <AppText variant="caption" color="primary">
                  {t('profile.merchantAccount')}
                </AppText>
              )}
            </View>
            {me.data?.emailVerified === false && <VerifyEmailBanner />}
            <View style={styles.section}>
              {common}
              <ListRow title={t('profile.addresses')} icon="home" onPress={() => navigation.navigate('Addresses')} />
              <ListRow title={t('profile.orders')} icon="receipt" onPress={() => navigation.navigate('Orders')} testID="profile-orders" />
              <ListRow
                title={t('notifications.title')}
                {...(unread > 0 ? { subtitle: t('notifications.newCount', { count: unread }) } : {})}
                icon="bell"
                onPress={() => navigation.navigate('Notifications')}
                testID="profile-notifications"
              />
              <ListRow title={t('auth.changePassword')} icon="lock" onPress={() => navigation.navigate('ChangePassword')} testID="profile-change-password" />
              <ListRow title={t('account.title')} icon="user" onPress={() => navigation.navigate('AccountData')} testID="profile-account-data" />
              {merchant && <ListRow title={t('profile.myMarket')} icon="store" onPress={() => navigation.navigate('MerchantMarkets')} testID="profile-my-market" />}
            </View>
            <Button title={t('profile.logout')} variant="danger" icon="logout" onPress={() => void logout()} style={styles.logout} />
          </>
        )}
      </ScrollView>
      <LanguageSheet visible={languageOpen} onClose={() => setLanguageOpen(false)} />
    </View>
  );
}

/** Shown until the email address is confirmed (B-39b); the link in the welcome mail confirms it. */
function VerifyEmailBanner() {
  const styles = useStyles();
  const { t } = useTranslation();
  const api = useApi();
  const resend = useMutation({ mutationFn: () => api.resendEmailVerification() });
  return (
    <View style={styles.banner} testID="verify-banner">
      <AppText variant="subhead">{t('verifyEmail.banner')}</AppText>
      {resend.isSuccess ? (
        <AppText variant="caption" color="textSecondary" accessibilityRole="alert">
          {t('verifyEmail.sent')}
        </AppText>
      ) : (
        <Button title={t('verifyEmail.resend')} variant="secondary" onPress={() => resend.mutate()} loading={resend.isPending} testID="verify-resend" />
      )}
      {resend.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(resend.error))}
        </AppText>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  content: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl },
  intro: { marginBottom: t.spacing.lg },
  account: { gap: 2, marginBottom: t.spacing.lg },
  section: { marginTop: t.spacing.lg },
  logout: { marginTop: t.spacing.xxl },
  banner: { gap: t.spacing.sm, padding: t.spacing.md, borderRadius: t.radii.tile, backgroundColor: t.colors.surfaceMuted, marginBottom: t.spacing.lg },
}));
