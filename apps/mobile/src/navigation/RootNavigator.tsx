import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { colors } from '../shared/theme';
import { LoadingState } from '../shared/ui';
import { AddressFormScreen } from '../features/address/screens/AddressFormScreen';
import { AddressListScreen } from '../features/address/screens/AddressListScreen';
import { useAuth } from '../features/auth/AuthProvider';
import { LoginScreen } from '../features/auth/screens/LoginScreen';
import { RegisterScreen } from '../features/auth/screens/RegisterScreen';
import { CategoryProductsScreen } from '../features/catalog/screens/CategoryProductsScreen';
import { DeliverySlotPickerScreen } from '../features/delivery/screens/DeliverySlotPickerScreen';
import { OrderConfirmationScreen } from '../features/orders/screens/OrderConfirmationScreen';
import { OrderDetailScreen } from '../features/orders/screens/OrderDetailScreen';
import { OrderListScreen } from '../features/orders/screens/OrderListScreen';
import { ProductDetailScreen } from '../features/product/screens/ProductDetailScreen';
import { PromotionsScreen } from '../features/promotions/screens/PromotionsScreen';
import { TabNavigator } from './TabNavigator';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const { status } = useAuth();

  if (status === 'restoring') {
    return <LoadingState label="Starting Dukkan" />;
  }

  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.primary,
        headerTitleStyle: { color: colors.text },
        contentStyle: { backgroundColor: colors.background },
        headerBackButtonDisplayMode: 'minimal',
      }}>
      {status === 'signedOut' ? (
        <Stack.Group>
          <Stack.Screen
            name="Login"
            component={LoginScreen}
            options={{ title: 'Sign in', animationTypeForReplace: 'pop' }}
          />
          <Stack.Screen name="Register" component={RegisterScreen} options={{ title: 'Create account' }} />
        </Stack.Group>
      ) : (
        <Stack.Group>
          <Stack.Screen name="Tabs" component={TabNavigator} options={{ headerShown: false }} />
          <Stack.Screen
            name="CategoryProducts"
            component={CategoryProductsScreen}
            options={({ route }) => ({ title: route.params.title })}
          />
          <Stack.Screen
            name="ProductDetail"
            component={ProductDetailScreen}
            options={({ route }) => ({ title: route.params.name ?? 'Product' })}
          />
          <Stack.Screen name="Promotions" component={PromotionsScreen} options={{ title: 'Offers' }} />
          <Stack.Screen
            name="AddressList"
            component={AddressListScreen}
            options={({ route }) => ({
              title: route.params?.selectForDelivery === true ? 'Deliver to' : 'Addresses',
            })}
          />
          <Stack.Screen
            name="AddressForm"
            component={AddressFormScreen}
            options={({ route }) => ({
              title: route.params?.addressId !== undefined ? 'Edit address' : 'New address',
            })}
          />
          <Stack.Screen
            name="DeliverySlotPicker"
            component={DeliverySlotPickerScreen}
            options={{ title: 'Delivery slot' }}
          />
          <Stack.Screen
            name="OrderConfirmation"
            component={OrderConfirmationScreen}
            options={{ title: 'Order placed', headerBackVisible: false, gestureEnabled: false }}
          />
          <Stack.Screen name="OrderList" component={OrderListScreen} options={{ title: 'Orders' }} />
          <Stack.Screen name="OrderDetail" component={OrderDetailScreen} options={{ title: 'Order' }} />
        </Stack.Group>
      )}
    </Stack.Navigator>
  );
}
