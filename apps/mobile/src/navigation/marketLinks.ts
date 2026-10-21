import type { NavigationProp } from '@react-navigation/native';

import type { RootStackParamList } from './types';

/** Anything that can navigate to root routes (root stack, shell tabs, market stack). */
type RootNavigation = Pick<NavigationProp<RootStackParamList>, 'navigate'>;

/** Opens a market's sub-app on its start page. */
export function openMarket(navigation: RootNavigation, marketId: string): void {
  navigation.navigate('Market', { marketId, screen: 'MarketTabs', params: { screen: 'MarketHome' } });
}

/** Opens a market's cart tab (from the Warenkörbe tab). */
export function openMarketCart(navigation: RootNavigation, marketId: string): void {
  navigation.navigate('Market', { marketId, screen: 'MarketTabs', params: { screen: 'MarketCart' } });
}

/** Opens a product inside its market's sub-app, with the market tabs underneath (back goes to the market). */
export function openMarketProduct(navigation: RootNavigation, marketId: string, productId: string): void {
  navigation.navigate('Market', { marketId, screen: 'MarketProduct', params: { productId }, initial: false });
}
