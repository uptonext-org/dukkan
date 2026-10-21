import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useMemo } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { tabBarOptions, tabIcon, tabLabel } from '../../navigation/tabBar';
import type { MarketStackParamList, MarketTabParamList, RootStackScreenProps } from '../../navigation/types';
import { formatAmount, formatMoneyForAccessibility } from '../../shared/format';
import { useTheme } from '../../shared/theme';
import { useCart } from '../cart/api/hooks';
import { MarketCartScreen } from '../cart/screens/MarketCartScreen';
import { CategoryProductsScreen } from '../catalog/screens/CategoryProductsScreen';
import { ProductDetailScreen } from '../catalog/screens/ProductDetailScreen';
import { MarketFavoritesScreen } from '../favorites/screens/MarketFavoritesScreen';
import { OrderConfirmationScreen } from '../orders/screens/OrderConfirmationScreen';
import { MarketSearchScreen } from '../search/screens/MarketSearchScreen';
import { MarketHeader } from './components/MarketHeader';
import { MarketContext, useMarketContext } from './MarketContext';
import { MarketHomeScreen } from './screens/MarketHomeScreen';

const Stack = createNativeStackNavigator<MarketStackParamList>();
const Tab = createBottomTabNavigator<MarketTabParamList>();

/** Cart tab badge: the cart total ("10,79"), spoken "Warenkorb, 10,79 Euro" (design-system.md §5). */
export function useCartBadge(marketId: string) {
  const { t } = useTranslation();
  const { data: cart } = useCart(marketId);
  if (cart === undefined || cart.itemCount === 0) {
    return { badge: undefined, label: tabLabel(t, t('market.tabs.cart'), 4, 4) };
  }
  return {
    badge: formatAmount(cart.total),
    label: t('market.tabs.cartWithTotal', { amount: formatMoneyForAccessibility(cart.total) }),
  };
}

function MarketTabs() {
  const theme = useTheme();
  const { t } = useTranslation();
  const { marketId } = useMarketContext();
  const { badge, label } = useCartBadge(marketId);
  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bg }}>
      <MarketHeader />
      <Tab.Navigator screenOptions={tabBarOptions(theme)}>
        <Tab.Screen name="MarketHome" component={MarketHomeScreen} options={{ title: t('market.tabs.discover'), tabBarIcon: tabIcon('home'), tabBarAccessibilityLabel: tabLabel(t, t('market.tabs.discover'), 1, 4) }} />
        <Tab.Screen name="MarketFavorites" component={MarketFavoritesScreen} options={{ title: t('market.tabs.favorites'), tabBarIcon: tabIcon('heart'), tabBarAccessibilityLabel: tabLabel(t, t('market.tabs.favorites'), 2, 4) }} />
        <Tab.Screen name="MarketSearch" component={MarketSearchScreen} options={{ title: t('market.tabs.search'), tabBarIcon: tabIcon('search'), tabBarAccessibilityLabel: tabLabel(t, t('market.tabs.search'), 3, 4) }} />
        <Tab.Screen
          name="MarketCart"
          component={MarketCartScreen}
          options={{
            title: t('market.tabs.cart'),
            tabBarIcon: tabIcon('basket'),
            ...(badge !== undefined ? { tabBarBadge: badge } : {}),
            tabBarAccessibilityLabel: label,
            tabBarButtonTestID: 'market-tab-cart',
          }}
        />
      </Tab.Navigator>
    </View>
  );
}

/** The market sub-app: its own stack with bottom tabs, product detail, listings and confirmation. */
export function MarketNavigator({ route, navigation }: RootStackScreenProps<'Market'>) {
  const { marketId } = route.params;
  const theme = useTheme();
  const value = useMemo(
    () => ({
      marketId,
      exit: () => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('Shell')),
    }),
    [marketId, navigation],
  );
  return (
    <MarketContext value={value}>
      <Stack.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: theme.colors.bg } }}>
        <Stack.Screen name="MarketTabs" component={MarketTabs} />
        <Stack.Screen name="MarketProduct" component={ProductDetailScreen} />
        <Stack.Screen name="MarketProducts" component={CategoryProductsScreen} />
        <Stack.Screen name="OrderConfirmation" component={OrderConfirmationScreen} options={{ gestureEnabled: false }} />
      </Stack.Navigator>
    </MarketContext>
  );
}
