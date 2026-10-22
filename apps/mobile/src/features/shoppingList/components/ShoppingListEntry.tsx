import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Icon } from '../../../shared/ui';

/** Market start page: opens the shopping list by voice, photo or video. */
export function ShoppingListEntry({ onPress }: { onPress: () => void }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${t('shoppingList.entryTitle')}. ${t('shoppingList.entrySubtitle')}`}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
      testID="shopping-list-entry">
      <View style={styles.icons}>
        <Icon name="mic" size={22} color={colors.primary} />
        <Icon name="video" size={22} color={colors.primary} />
      </View>
      <View style={styles.text}>
        <AppText variant="bodyStrong">{t('shoppingList.entryTitle')}</AppText>
        <AppText variant="subhead" color="textSecondary">
          {t('shoppingList.entrySubtitle')}
        </AppText>
      </View>
      <Icon name="chevron" size={18} color={colors.textSecondary} />
    </Pressable>
  );
}

const useStyles = makeStyles((t) => ({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: t.spacing.md,
    padding: t.spacing.md,
    borderRadius: 16,
    backgroundColor: t.colors.surfaceMuted,
  },
  pressed: { opacity: 0.7 },
  icons: { flexDirection: 'row', gap: 4 },
  text: { flex: 1, gap: 2 },
}));
