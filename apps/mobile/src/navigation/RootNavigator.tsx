import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { AddressFormScreen } from '../features/addresses/screens/AddressFormScreen';
import { AddressListScreen } from '../features/addresses/screens/AddressListScreen';
import { useAuth } from '../features/auth/AuthProvider';
import { ChangePasswordScreen } from '../features/auth/screens/ChangePasswordScreen';
import { AccountDataScreen } from '../features/profile/screens/AccountDataScreen';
import { ResetPasswordScreen } from '../features/auth/screens/ResetPasswordScreen';
import { VerifyEmailScreen } from '../features/auth/screens/VerifyEmailScreen';
import { MarketNavigator } from '../features/market/MarketNavigator';
import { NotificationsScreen } from '../features/notifications/screens/NotificationsScreen';
import { AddProductScreen } from '../features/merchant/screens/AddProductScreen';
import { FeedbackScreen } from '../features/merchant/screens/FeedbackScreen';
import { ListingsScreen } from '../features/merchant/screens/ListingsScreen';
import { AdminMarketsScreen } from '../features/admin/screens/AdminMarketsScreen';
import { MarketHubScreen } from '../features/merchant/screens/MarketHubScreen';
import { MarketProfileFormScreen } from '../features/merchant/screens/MarketProfileFormScreen';
import { MerchantMarketsScreen } from '../features/merchant/screens/MerchantMarketsScreen';
import { MerchantOrdersScreen } from '../features/merchant/screens/MerchantOrdersScreen';
import { PromotionFormScreen } from '../features/merchant/screens/PromotionFormScreen';
import { PromotionsScreen } from '../features/merchant/screens/PromotionsScreen';
import { MapPickerScreen } from '../features/onboarding/screens/MapPickerScreen';
import { OnboardingScreen } from '../features/onboarding/screens/OnboardingScreen';
import { OrderDetailScreen } from '../features/orders/screens/OrderDetailScreen';
import { OrderListScreen } from '../features/orders/screens/OrderListScreen';
import { useChosenPlace } from '../shared/location';
import { useTheme } from '../shared/theme';
import { LoadingState } from '../shared/ui';
import { ShellTabs } from './ShellTabs';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * All headers are JS (`headerShown: false`, ScreenHeader) so they mirror instantly in Arabic.
 * The native stack still provides the iOS back swipe and Android back button.
 */
export function RootNavigator() {
  const { status } = useAuth();
  const { status: placeStatus, place } = useChosenPlace();
  const { colors } = useTheme();

  if (status === 'restoring' || placeStatus === 'restoring') {
    return <LoadingState />;
  }

  return (
    // First launch uses its own route names (Welcome, WelcomeMap): once a place exists those
    // routes disappear from the config and React Navigation lands on the shell.
    <Stack.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }}>
      {place === null ? (
        <Stack.Group>
          <Stack.Screen name="Welcome" component={OnboardingScreen} />
          <Stack.Screen name="WelcomeMap" component={MapPickerScreen} />
          {/* The reset link may be the first thing a new install opens. */}
          <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} options={{ presentation: 'modal' }} />
          <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} options={{ presentation: 'modal' }} />
        </Stack.Group>
      ) : (
        <Stack.Group>
          <Stack.Screen name="Shell" component={ShellTabs} />
          <Stack.Screen name="Market" component={MarketNavigator} />
          <Stack.Screen name="Onboarding" component={OnboardingScreen} options={{ presentation: 'modal' }} />
          <Stack.Screen name="MapPicker" component={MapPickerScreen} />
          <Stack.Screen name="Addresses" component={AddressListScreen} />
          <Stack.Screen name="AddressForm" component={AddressFormScreen} />
          <Stack.Screen name="Orders" component={OrderListScreen} />
          <Stack.Screen name="OrderDetail" component={OrderDetailScreen} />
          <Stack.Screen name="AdminMarkets" component={AdminMarketsScreen} />
          <Stack.Screen name="MerchantMarkets" component={MerchantMarketsScreen} />
          <Stack.Screen name="MerchantMarketForm" component={MarketProfileFormScreen} />
          <Stack.Screen name="MerchantMarketHub" component={MarketHubScreen} />
          <Stack.Screen name="MerchantListings" component={ListingsScreen} />
          <Stack.Screen name="MerchantAddProduct" component={AddProductScreen} />
          <Stack.Screen name="MerchantPromotions" component={PromotionsScreen} />
          <Stack.Screen name="MerchantFeedback" component={FeedbackScreen} />
          <Stack.Screen name="MerchantPromotionForm" component={PromotionFormScreen} />
          <Stack.Screen name="MerchantOrders" component={MerchantOrdersScreen} />
          <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} options={{ presentation: 'modal' }} />
          <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} options={{ presentation: 'modal' }} />
          <Stack.Screen name="ChangePassword" component={ChangePasswordScreen} />
          <Stack.Screen name="AccountData" component={AccountDataScreen} />
          <Stack.Screen name="Notifications" component={NotificationsScreen} />
        </Stack.Group>
      )}
    </Stack.Navigator>
  );
}
