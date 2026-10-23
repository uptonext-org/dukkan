import type { LinkingOptions, PathConfigMap } from '@react-navigation/native';

import type { ProductFilter, RootStackParamList } from './types';

function parseFilter(value: string): ProductFilter {
  try {
    const parsed: unknown = JSON.parse(value);
    return typeof parsed === 'object' && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * One path per screen. The app opens these from mails and links (dukkan://reset-password?token=…);
 * the web app (apps/customer-web) shows them in the address bar, so a reload or a shared link lands
 * on the same screen. Screens of the first-launch group exist only until a place is chosen.
 */
export const SCREEN_PATHS: PathConfigMap<RootStackParamList> = {
  Welcome: 'welcome',
  WelcomeMap: 'welcome/map',
  Onboarding: 'place',
  MapPicker: 'place/map',
  Shell: {
    path: '',
    screens: { Discover: '', GlobalSearch: 'search', Carts: 'carts', Profile: 'profile' },
  },
  Market: {
    path: 'markets/:marketId',
    screens: {
      MarketTabs: {
        path: '',
        screens: { MarketHome: '', MarketFavorites: 'favorites', MarketSearch: 'search', MarketCart: 'cart' },
      },
      MarketProduct: 'products/:productId',
      MarketProducts: { path: 'products', parse: { filter: parseFilter }, stringify: { filter: (f: ProductFilter) => JSON.stringify(f) } },
      OrderConfirmation: 'orders/:orderId/confirmation',
      ShoppingList: 'shopping-list',
    },
  },
  Addresses: 'addresses',
  AddressForm: 'addresses/edit',
  Orders: 'orders',
  OrderDetail: 'orders/:orderId',
  MerchantMarkets: 'merchant',
  MerchantMarketForm: 'merchant/market',
  MerchantMarketHub: 'merchant/markets/:marketId',
  MerchantListings: 'merchant/markets/:marketId/listings',
  MerchantAddProduct: 'merchant/markets/:marketId/add-product',
  MerchantPromotions: 'merchant/markets/:marketId/promotions',
  MerchantPromotionForm: 'merchant/markets/:marketId/promotions/new',
  MerchantOrders: 'merchant/markets/:marketId/orders',
  ResetPassword: 'reset-password',
  VerifyEmail: 'verify-email',
  ChangePassword: 'account/password',
  AccountData: 'account/data',
  Notifications: 'notifications',
};

/**
 * Deep links. The app scheme is registered in AndroidManifest and Info.plist; https prefixes
 * (the web app's origin, later App Links / Universal Links) are added by the caller.
 */
export function buildLinking(prefixes: string[] = ['dukkan://']): LinkingOptions<RootStackParamList> {
  return { prefixes, config: { screens: SCREEN_PATHS } };
}
