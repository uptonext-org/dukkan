import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useTranslation } from 'react-i18next';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { forSpeech } from '../shared/format';
import { useCarts } from '../features/cart/api/hooks';
import { CartsScreen } from '../features/cart/screens/CartsScreen';
import { DiscoverScreen } from '../features/marketplace/screens/DiscoverScreen';
import { ProfileScreen } from '../features/profile/screens/ProfileScreen';
import { GlobalSearchScreen } from '../features/search/screens/GlobalSearchScreen';
import { useTheme } from '../shared/theme';
import { tabBarOptions, tabIcon, tabLabel } from './tabBar';
import type { ShellTabParamList } from './types';

const Tab = createBottomTabNavigator<ShellTabParamList>();

/** Marketplace shell: Entdecken · Suchen · Warenkörbe · Profil. */
export function ShellTabs() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { t } = useTranslation();
  const { data: carts } = useCarts();
  const openCarts = carts?.length ?? 0;
  return (
    <Tab.Navigator screenOptions={tabBarOptions(theme, insets.bottom)}>
      <Tab.Screen name="Discover" component={DiscoverScreen} options={{ title: t('shell.tabs.discover'), tabBarIcon: tabIcon('store'), tabBarAccessibilityLabel: tabLabel(t, t('shell.tabs.discover'), 1, 4) }} />
      <Tab.Screen name="GlobalSearch" component={GlobalSearchScreen} options={{ title: t('shell.tabs.search'), tabBarIcon: tabIcon('search'), tabBarAccessibilityLabel: tabLabel(t, t('shell.tabs.search'), 2, 4) }} />
      <Tab.Screen
        name="Carts"
        component={CartsScreen}
        options={{
          title: t('shell.tabs.carts'),
          tabBarIcon: tabIcon('basket'),
          ...(openCarts > 0 ? { tabBarBadge: openCarts } : {}),
          tabBarAccessibilityLabel:
            openCarts > 0 ? forSpeech(t('shell.tabs.cartsWithCount', { count: openCarts })) : tabLabel(t, t('shell.tabs.carts'), 3, 4),
        }}
      />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: t('shell.tabs.profile'), tabBarIcon: tabIcon('user'), tabBarAccessibilityLabel: tabLabel(t, t('shell.tabs.profile'), 4, 4) }} />
    </Tab.Navigator>
  );
}
