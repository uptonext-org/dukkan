import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import {
  useNavigation,
  type CompositeScreenProps,
  type NavigatorScreenParams,
} from '@react-navigation/native';
import type { NativeStackNavigationProp, NativeStackScreenProps } from '@react-navigation/native-stack';

import type { ListMarketProductsParams } from '../shared/api';

export type ShellTabParamList = {
  Discover: undefined;
  GlobalSearch: undefined;
  Carts: undefined;
  Profile: undefined;
};

export type MarketTabParamList = {
  MarketHome: undefined;
  MarketFavorites: undefined;
  MarketSearch: undefined;
  MarketCart: undefined;
};

export type ProductFilter = Pick<ListMarketProductsParams, 'categoryId' | 'promotedOnly' | 'newOnly' | 'organicOnly'>;

export type MarketStackParamList = {
  MarketTabs: NavigatorScreenParams<MarketTabParamList> | undefined;
  MarketProduct: { productId: string };
  MarketProducts: { title: string; filter: ProductFilter };
  OrderConfirmation: { orderId: string };
};

export type RootStackParamList = {
  /** First launch ("Wo wohnst du?") and its map. */
  Welcome: undefined;
  WelcomeMap: undefined;
  /** Changing the place later (from the shell header or profile). */
  Onboarding: { change?: boolean } | undefined;
  MapPicker: undefined;
  Shell: NavigatorScreenParams<ShellTabParamList> | undefined;
  /** The market sub-app; `marketId` is read by every nested screen through MarketContext. */
  Market: { marketId: string } & NavigatorScreenParams<MarketStackParamList>;
  Addresses: undefined;
  AddressForm: { addressId?: string } | undefined;
  Orders: undefined;
  OrderDetail: { orderId: string };
  MerchantMarkets: undefined;
  MerchantMarketForm: { marketId?: string } | undefined;
  MerchantMarketHub: { marketId: string };
  MerchantListings: { marketId: string };
  MerchantAddProduct: { marketId: string };
  MerchantPromotions: { marketId: string };
  MerchantPromotionForm: { marketId: string };
  MerchantOrders: { marketId: string };
  /** Deep link from the reset mail: dukkan://reset-password?token=… */
  ResetPassword: { token: string };
  ChangePassword: undefined;
  Notifications: undefined;
};

export type RootStackScreenProps<T extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, T>;

export type ShellTabScreenProps<T extends keyof ShellTabParamList> = CompositeScreenProps<
  BottomTabScreenProps<ShellTabParamList, T>,
  NativeStackScreenProps<RootStackParamList>
>;

export type MarketStackScreenProps<T extends keyof MarketStackParamList> = CompositeScreenProps<
  NativeStackScreenProps<MarketStackParamList, T>,
  NativeStackScreenProps<RootStackParamList>
>;

export type MarketTabScreenProps<T extends keyof MarketTabParamList> = CompositeScreenProps<
  BottomTabScreenProps<MarketTabParamList, T>,
  MarketStackScreenProps<'MarketTabs'>
>;

export type RootNavigation = NativeStackNavigationProp<RootStackParamList>;

export function useRootNavigation(): RootNavigation {
  return useNavigation<RootNavigation>();
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ReactNavigation {
    // eslint-disable-next-line @typescript-eslint/no-empty-object-type
    interface RootParamList extends RootStackParamList {}
  }
}
