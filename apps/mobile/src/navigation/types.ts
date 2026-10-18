import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import {
  useNavigation,
  useRoute,
  type CompositeScreenProps,
  type NavigatorScreenParams,
  type RouteProp,
} from '@react-navigation/native';
import type { NativeStackNavigationProp, NativeStackScreenProps } from '@react-navigation/native-stack';

export type TabParamList = {
  Home: undefined;
  Search: { promotedOnly?: boolean } | undefined;
  Cart: undefined;
  Profile: undefined;
};

export type RootStackParamList = {
  Login: undefined;
  Register: undefined;
  Tabs: NavigatorScreenParams<TabParamList> | undefined;
  CategoryProducts: { categoryId: string; title: string };
  ProductDetail: { productId: string; name?: string };
  Promotions: undefined;
  AddressList: { selectForDelivery?: boolean } | undefined;
  AddressForm: { addressId?: string; selectForDelivery?: boolean } | undefined;
  DeliverySlotPicker: { addressId: string };
  OrderConfirmation: { orderId: string };
  OrderList: undefined;
  OrderDetail: { orderId: string };
};

export type RootStackScreenProps<T extends keyof RootStackParamList> = NativeStackScreenProps<
  RootStackParamList,
  T
>;

export type TabScreenProps<T extends keyof TabParamList> = CompositeScreenProps<
  BottomTabScreenProps<TabParamList, T>,
  NativeStackScreenProps<RootStackParamList>
>;

export type RootNavigation = NativeStackNavigationProp<RootStackParamList>;

/** Typed `useNavigation` for screens and components anywhere in the tree. */
export function useRootNavigation(): RootNavigation {
  return useNavigation<RootNavigation>();
}

export function useRootRoute<T extends keyof RootStackParamList>(): RouteProp<RootStackParamList, T> {
  return useRoute<RouteProp<RootStackParamList, T>>();
}

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ReactNavigation {
    // eslint-disable-next-line @typescript-eslint/no-empty-object-type
    interface RootParamList extends RootStackParamList {}
  }
}
