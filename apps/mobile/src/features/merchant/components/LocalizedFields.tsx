import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { LANGUAGES, type Language } from '../../../shared/i18n';
import { TextField } from '../../../shared/ui';

/**
 * One text field per content language (de/en/ar). Each field takes the writing direction of its
 * language, independent of the UI language, so Arabic is typed right-to-left even in German UI.
 */
export function LocalizedFields({
  label,
  values,
  onChange,
  primary,
  error,
  multiline = false,
  testIDPrefix,
}: {
  label: string;
  values: Record<Language, string>;
  onChange: (language: Language, text: string) => void;
  primary: Language;
  error?: string | undefined;
  multiline?: boolean;
  testIDPrefix?: string;
}) {
  const { t } = useTranslation();
  return (
    <View>
      {LANGUAGES.map((l) => (
        <TextField
          key={l.code}
          label={`${label} · ${l.nativeName}${l.code === primary ? ` (${t('merchant.primary')})` : ''}`}
          value={values[l.code]}
          onChangeText={(text) => onChange(l.code, text)}
          error={l.code === primary ? error : undefined}
          multiline={multiline}
          {...(testIDPrefix !== undefined ? { testID: `${testIDPrefix}-${l.code}` } : {})}
          inputDirection={l.direction}
        />
      ))}
    </View>
  );
}
