import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey, type TranslationKind } from '../../../shared/api';
import { LANGUAGES, type Language } from '../../../shared/i18n';
import { AppText, Button, TextField } from '../../../shared/ui';
import { useTranslateTexts } from '../api/hooks';

/**
 * One text field per content language (de/en/ar). Each field takes the writing direction of its
 * language, independent of the UI language, so Arabic is typed right-to-left even in German UI.
 * With `translateKind`, a button fills the empty languages with machine translations (B-38) for
 * the merchant to review; filled fields are never overwritten.
 */
export function LocalizedFields({
  label,
  values,
  onChange,
  primary,
  error,
  multiline = false,
  testIDPrefix,
  translateKind,
}: {
  label: string;
  values: Record<Language, string>;
  onChange: (language: Language, text: string) => void;
  primary: Language;
  error?: string | undefined;
  multiline?: boolean;
  testIDPrefix?: string;
  translateKind?: TranslationKind;
}) {
  const { t } = useTranslation();
  const translate = useTranslateTexts();
  const filled = LANGUAGES.filter((l) => values[l.code].trim() !== '');
  const canTranslate = translateKind !== undefined && filled.length > 0 && filled.length < LANGUAGES.length;
  const prefix = testIDPrefix ?? 'localized';

  const run = () => {
    if (translateKind === undefined) {
      return;
    }
    const source = Object.fromEntries(filled.map((l) => [l.code, values[l.code].trim()]));
    translate.mutate(
      { kind: translateKind, fields: [{ key: 'text', values: source }] },
      {
        onSuccess: (result) => {
          const suggestions = result.fields.find((f) => f.key === 'text')?.values ?? {};
          for (const l of LANGUAGES) {
            const text = suggestions[l.code];
            // The merchant may have typed meanwhile: only fill what is still empty.
            if (text !== undefined && text !== '' && values[l.code].trim() === '') {
              onChange(l.code, text);
            }
          }
        },
      },
    );
  };

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
      {canTranslate && (
        <Button
          title={translate.isPending ? t('merchant.translate.working') : t('merchant.translate.button')}
          accessibilityHint={t('merchant.translate.hint')}
          variant="ghost"
          icon="sparkles"
          onPress={run}
          loading={translate.isPending}
          testID={`${prefix}-translate`}
        />
      )}
      {translate.isSuccess && (
        <AppText variant="caption" color="textSecondary" accessibilityLiveRegion="polite" testID={`${prefix}-translated`}>
          {t(translate.data.fields.length > 0 ? 'merchant.translate.review' : 'merchant.translate.nothing')}
        </AppText>
      )}
      {translate.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert" testID={`${prefix}-translate-error`}>
          {t(errorMessageKey(translate.error))}
        </AppText>
      )}
    </View>
  );
}
