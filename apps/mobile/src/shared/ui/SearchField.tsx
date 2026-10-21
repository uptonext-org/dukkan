import { Pressable, TextInput, View, type TextInputProps } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../format/bidi';
import { makeStyles, useTheme } from '../theme';
import { Icon } from './Icon';

interface SearchFieldProps extends Omit<TextInputProps, 'style' | 'onChangeText' | 'value'> {
  value: string;
  onChangeText: (text: string) => void;
  placeholder: string;
}

/** Rounded `surfaceMuted` search field (search-fields.md: a clear button and a magnifier). */
export function SearchField({ value, onChangeText, placeholder, ...props }: SearchFieldProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  return (
    <View style={styles.field}>
      <Icon name="search" size={22} color={colors.textSecondary} />
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.textPlaceholder}
        accessibilityLabel={forSpeech(placeholder)}
        accessibilityRole="search"
        returnKeyType="search"
        autoCorrect={false}
        clearButtonMode="never"
        style={styles.input}
        {...props}
      />
      {value.length > 0 && (
        <Pressable
          onPress={() => onChangeText('')}
          accessibilityRole="button"
          accessibilityLabel={t('common.clearSearch')}
          hitSlop={8}
          style={styles.clear}>
          <Icon name="close" size={18} color={colors.textSecondary} />
        </Pressable>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: t.spacing.sm,
    backgroundColor: t.colors.surfaceMuted,
    borderRadius: t.radii.tile,
    paddingHorizontal: t.spacing.md,
    minHeight: 52,
  },
  input: { ...t.type.body, flex: 1, color: t.colors.text, paddingVertical: t.spacing.sm, textAlign: 'auto' as const },
  clear: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
}));
