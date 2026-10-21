import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { useApi } from '../../../shared/api';
import { LANGUAGES, useLanguage, type Language } from '../../../shared/i18n';
import { useTheme } from '../../../shared/theme';
import { AppText, Icon, ListRow, Sheet } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';

/** Deutsch / English / العربية. Each name is shown in its own language and script. */
export function LanguageSheet({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const { language, setLanguage } = useLanguage();
  const { status } = useAuth();
  const api = useApi();
  const [restartHint, setRestartHint] = useState(false);
  const savePreference = useMutation({ mutationFn: (code: Language) => api.updateCurrentUser({ preferredLocale: code }) });

  const choose = async (code: Language) => {
    if (code === language) {
      onClose();
      return;
    }
    const needsRestart = await setLanguage(code);
    if (status === 'signedIn') {
      savePreference.mutate(code);
    }
    setRestartHint(needsRestart);
    if (!needsRestart) {
      onClose();
    }
  };

  return (
    <Sheet
      visible={visible}
      onClose={() => {
        setRestartHint(false);
        onClose();
      }}
      title={t('language.title')}
      testID="language-sheet">
      <View accessibilityRole="radiogroup">
        {LANGUAGES.map((l) => {
          const selected = l.code === language;
          return (
            <ListRow
              key={l.code}
              title={l.nativeName}
              chevron={false}
              selected={selected}
              onPress={() => void choose(l.code)}
              accessibilityLabel={l.nativeName}
              trailing={selected ? <Icon name="check" color={colors.primary} /> : undefined}
              testID={`language-${l.code}`}
            />
          );
        })}
      </View>
      {restartHint && (
        <AppText variant="subhead" color="textSecondary" accessibilityLiveRegion="polite" style={{ marginTop: 12 }}>
          {t('language.restartHint')}
        </AppText>
      )}
    </Sheet>
  );
}
