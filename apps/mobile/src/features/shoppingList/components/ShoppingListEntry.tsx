import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Icon } from '../../../shared/ui';
import { useShoppingListSwitches } from '../api/hooks';

/**
 * Market start page: opens the shopping list. Shown only while platform admins have switched it
 * on; title and icons name only the ways that are switched on (typing, speaking, photo/video).
 */
export function ShoppingListEntry({ onPress }: { onPress: () => void }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { enabled, voice, media } = useShoppingListSwitches();
  if (!enabled) {
    return null;
  }
  const title = t(voice && media ? 'shoppingList.entryTitle' : voice ? 'shoppingList.entryTitleVoice' : media ? 'shoppingList.entryTitleMedia' : 'shoppingList.entryTitleType');
  const subtitle = t(voice || media ? 'shoppingList.entrySubtitle' : 'shoppingList.entrySubtitleType');
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${subtitle}`}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
      testID="shopping-list-entry">
      <View style={styles.icons}>
        {voice && <Icon name="mic" size={22} color={colors.primary} />}
        {media && <Icon name="video" size={22} color={colors.primary} />}
        {!voice && !media && <Icon name="list" size={22} color={colors.primary} />}
      </View>
      <View style={styles.text}>
        <AppText variant="bodyStrong">{title}</AppText>
        <AppText variant="subhead" color="textSecondary">
          {subtitle}
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
