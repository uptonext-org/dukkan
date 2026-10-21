import { useNavigation, type NavigationProp } from '@react-navigation/native';
import { useState, type ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';

import type { RootStackParamList } from '../../../navigation/types';
import { useChosenPlace } from '../../../shared/location';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Icon, IconButton } from '../../../shared/ui';
import { LanguageSheet } from '../../profile/components/LanguageSheet';

/**
 * Shell header: location button at the start ("📍 St. Georg ›" with a pin icon), then the
 * globe language button and the account button at the end (decision 2: top right in LTR).
 */
export function ShellHeader({ title, showAccount = true }: { title?: string; showAccount?: boolean }) {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const navigation = useNavigation<NavigationProp<RootStackParamList>>();
  const { place } = useChosenPlace();
  const [languageOpen, setLanguageOpen] = useState(false);
  let start: ReactNode;
  if (title !== undefined) {
    start = (
      <AppText variant="title" accessibilityRole="header" style={styles.flex} numberOfLines={1}>
        {title}
      </AppText>
    );
  } else {
    start = (
      <Pressable
        onPress={() => navigation.navigate('Onboarding', { change: true })}
        accessibilityRole="button"
        accessibilityLabel={t('shell.locationButton', { place: place?.shortLabel ?? '' })}
        accessibilityHint={t('shell.locationHint')}
        style={({ pressed }) => [styles.location, pressed && styles.pressed]}
        testID="location-button">
        <Icon name="pin" size={20} color={colors.primary} />
        <AppText variant="headline" color="primary" dense numberOfLines={1} style={styles.shrink}>
          {place?.shortLabel ?? t('shell.chooseLocation')}
        </AppText>
        <Icon name="chevron" size={18} color={colors.primary} />
      </Pressable>
    );
  }
  return (
    <View style={[styles.header, { paddingTop: insets.top + 4 }]}>
      {start}
      <IconButton icon="globe" label={t('language.button')} onPress={() => setLanguageOpen(true)} testID="globe-button" />
      {showAccount && (
        <IconButton
          icon="user"
          label={t('shell.account')}
          onPress={() => navigation.navigate('Shell', { screen: 'Profile' })}
        />
      )}
      <LanguageSheet visible={languageOpen} onClose={() => setLanguageOpen(false)} />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingStart: t.layout.gutter,
    paddingEnd: t.spacing.xs,
    paddingBottom: t.spacing.xs,
    backgroundColor: t.colors.bg,
    gap: t.spacing.xs,
  },
  location: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: t.spacing.xs, minHeight: 44 },
  flex: { flex: 1 },
  shrink: { flexShrink: 1 },
  pressed: { opacity: 0.6 },
}));
