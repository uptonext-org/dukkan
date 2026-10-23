import { Linking, Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../../shared/format';
import { makeStyles, useTheme } from '../../shared/theme';
import { AppText, Button, Icon } from '../../shared/ui';

/** "I accept the terms and have read the privacy notice", with both documents one tap away. */
export function TermsConsent({
  accepted,
  onChange,
  termsUrl,
  privacyUrl,
  error,
}: {
  accepted: boolean;
  onChange: (accepted: boolean) => void;
  termsUrl: string | null;
  privacyUrl: string | null;
  error: boolean;
}) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const open = (url: string) => () => void Linking.openURL(url).catch(() => undefined);
  return (
    <View style={styles.root} testID="terms-consent">
      <Pressable
        onPress={() => onChange(!accepted)}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: accepted }}
        accessibilityLabel={forSpeech(t('legal.consent'))}
        style={styles.row}
        hitSlop={6}
        testID="terms-accept">
        <View style={[styles.box, accepted && styles.boxOn, error && !accepted && styles.boxError]}>
          {accepted && <Icon name="check" size={16} color={colors.onAccent} />}
        </View>
        <AppText variant="subhead" style={styles.text}>
          {t('legal.consent')}
        </AppText>
      </Pressable>
      <View style={styles.links}>
        {termsUrl !== null && <Button title={t('legal.readTerms')} variant="ghost" onPress={open(termsUrl)} testID="terms-read" />}
        {privacyUrl !== null && <Button title={t('legal.readPrivacy')} variant="ghost" onPress={open(privacyUrl)} testID="privacy-read" />}
      </View>
      {error && !accepted && (
        <AppText variant="caption" color="accent" accessibilityRole="alert" accessibilityLiveRegion="polite">
          {t('legal.acceptRequired')}
        </AppText>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  root: { marginBottom: t.spacing.md },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: t.spacing.sm },
  box: {
    width: 24,
    height: 24,
    marginTop: 2,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: t.colors.controlTrackOff,
    alignItems: 'center',
    justifyContent: 'center',
  },
  boxOn: { backgroundColor: t.colors.primary, borderColor: t.colors.primary },
  boxError: { borderColor: t.colors.accent },
  text: { flex: 1 },
  links: { flexDirection: 'row', flexWrap: 'wrap', gap: t.spacing.sm, marginStart: 32 },
}));
