import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { StyleSheet, View } from 'react-native';

import { colors } from '../shared/theme';
import { CartScreen } from '../features/cart/screens/CartScreen';
import { useCart } from '../features/cart/api/hooks';
import { HomeScreen } from '../features/catalog/screens/HomeScreen';
import { ProfileScreen } from '../features/profile/screens/ProfileScreen';
import { SearchScreen } from '../features/search/screens/SearchScreen';
import type { TabParamList } from './types';

const Tab = createBottomTabNavigator<TabParamList>();

/** Icon-free tab indicator: a small bar that is filled for the focused tab. */
function TabIndicator({ focused }: { focused: boolean }) {
  return <View style={[styles.indicator, focused && styles.indicatorFocused]} />;
}

const renderIndicator = ({ focused }: { focused: boolean }) => <TabIndicator focused={focused} />;

export function TabNavigator() {
  const { data: cart } = useCart();
  const count = cart?.itemCount ?? 0;
  return (
    <Tab.Navigator
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: { backgroundColor: colors.surface },
        tabBarLabelStyle: styles.label,
        tabBarIcon: renderIndicator,
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.text,
      }}>
      <Tab.Screen name="Home" component={HomeScreen} options={{ title: 'Dukkan', tabBarLabel: 'Shop' }} />
      <Tab.Screen name="Search" component={SearchScreen} options={{ title: 'Search' }} />
      <Tab.Screen
        name="Cart"
        component={CartScreen}
        options={{
          title: 'Cart',
          ...(count > 0 ? { tabBarBadge: count } : {}),
          tabBarAccessibilityLabel: count > 0 ? `Cart, ${count} items` : 'Cart',
          tabBarBadgeStyle: { backgroundColor: colors.promo, color: colors.onPromo },
        }}
      />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: 'Profile' }} />
    </Tab.Navigator>
  );
}

const styles = StyleSheet.create({
  label: { fontSize: 13, fontWeight: '600' },
  indicator: { width: 24, height: 4, borderRadius: 2, backgroundColor: 'transparent' },
  indicatorFocused: { backgroundColor: colors.primary },
});
