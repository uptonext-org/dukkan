import type { BottomTabNavigationOptions } from '@react-navigation/bottom-tabs';
import type { TFunction } from 'i18next';

import type { Theme } from '../shared/theme';
import { Icon, type IconName } from '../shared/ui';

/**
 * One shared iOS-style tab bar on both platforms (design-system.md §5): labels under icons,
 * active colour `accent`, 11/13 labels. Deliberate cross-platform choice (cross-platform.md).
 */
export function tabBarOptions(theme: Theme): BottomTabNavigationOptions {
  return {
    headerShown: false,
    tabBarActiveTintColor: theme.colors.accent,
    tabBarInactiveTintColor: theme.colors.textSecondary,
    tabBarStyle: { backgroundColor: theme.colors.surface, borderTopColor: theme.colors.separator },
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
  return t('common.tab', { title, index, count });
}

export function tabIcon(name: IconName) {
  return function TabIcon({ color, focused }: { color: string; focused: boolean; size: number }) {
    return <Icon name={name} size={24} color={color} strokeWidth={focused ? 2.4 : 1.8} />;
  };
}
