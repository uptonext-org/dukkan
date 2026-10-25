import { useNavigation, type NavigationProp } from '@react-navigation/native';
import { useCallback, useEffect } from 'react';
import { Alert } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketStackParamList } from '../../navigation/types';
import { errorMessageKey, type ProductSummary } from '../../shared/api';
import { useCartQuantities } from '../cart/api/hooks';
import { useToggleProductFavorite } from '../favorites/api/hooks';
import { useAcceptsOrders } from '../market/api/hooks';

/** Everything a product tile needs inside a market: quantities, add/change, open, heart. */
export function useProductActions(marketId: string) {
  const navigation = useNavigation<NavigationProp<MarketStackParamList>>();
  const cart = useCartQuantities(marketId);
  const favorites = useToggleProductFavorite();
  // A market that takes no orders is a showcase: products without "+" or quantity.
  const orderable = useAcceptsOrders(marketId);
  const { t } = useTranslation();
  const { setQuantity, error, reset } = cart;
  useEffect(() => {
    if (error !== null) {
      Alert.alert(t('cart.updateFailed'), t(errorMessageKey(error)), [{ text: t('common.ok'), onPress: reset }]);
    }
  }, [error, reset, t]);
  const { toggle } = favorites;
  const openProduct = useCallback((product: ProductSummary) => navigation.navigate('MarketProduct', { productId: product.id }), [navigation]);
  const changeQuantity = useCallback((product: ProductSummary, quantity: number) => setQuantity(product.id, quantity), [setQuantity]);
  const toggleFavorite = useCallback((product: ProductSummary) => toggle(product.id, product.favorite === true), [toggle]);
  return {
    quantities: cart.quantities,
    busyProductId: cart.busyProductId,
    cartError: cart.error,
    resetCartError: cart.reset,
    openProduct,
    changeQuantity,
    toggleFavorite,
    orderable,
  };
}
