import type { BottomTabNavigationOptions } from '@react-navigation/bottom-tabs';
import type { TFunction } from 'i18next';

import { forSpeech } from '../shared/format';
import type { Theme } from '../shared/theme';
import { Icon, type IconName } from '../shared/ui';

/** UIKit tab bar height without the safe-area inset (React Navigation's default). */
const TAB_BAR_HEIGHT = 49;

/**
 * One shared iOS-style tab bar on both platforms (design-system.md §5): labels under icons,
 * active colour `accent`, 11/13 labels. Deliberate cross-platform choice (cross-platform.md).
 */
export function tabBarOptions(theme: Theme, bottomInset: number): BottomTabNavigationOptions {
  // IBM Plex Sans Arabic has tall ascenders and deep descenders: at 11 pt it needs about 18 pt of
  // line height (theme.type.tabLabel already carries the Arabic leading). The UIKit bar height of
  // 49 pt leaves room for the Latin 13 pt line only, so the bar grows by the extra leading. A
  // custom height replaces React Navigation's own, so the bottom safe-area inset is added here.
  const extraLeading = Math.max(0, (theme.type.tabLabel.lineHeight ?? 13) - 13);
  return {
    headerShown: false,
    tabBarActiveTintColor: theme.colors.accent,
    tabBarInactiveTintColor: theme.colors.textSecondary,
    tabBarStyle: {
      backgroundColor: theme.colors.surface,
      borderTopColor: theme.colors.separator,
      height: TAB_BAR_HEIGHT + extraLeading + bottomInset,
    },
    tabBarLabelStyle: theme.type.tabLabel,
    tabBarBadgeStyle: {
      backgroundColor: theme.colors.accentFill,
      color: theme.colors.onAccent,
      fontFamily: theme.type.caption.fontFamily,
      fontSize: 11,
    },
    tabBarAllowFontScaling: true,
  };
}

/** Localised tab label ("Suchen, Tab 2 von 4"); React Navigation's default appends English text. */
export function tabLabel(t: TFunction, title: string, index: number, count: number): string {
  return forSpeech(t('common.tab', { title, index, count }));
}

export function tabIcon(name: IconName) {
  return function TabIcon({ color, focused }: { color: string; focused: boolean; size: number }) {
    return <Icon name={name} size={24} color={color} strokeWidth={focused ? 2.4 : 1.8} />;
  };
}
