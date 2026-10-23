import { useState } from 'react';
import { Alert, Pressable, ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketTabScreenProps } from '../../../navigation/types';
import { errorMessageKey } from '../../../shared/api';
import { formatMoney, forSpeech } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, EmptyState, Icon, IconButton, Screen, queryState } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';
import { useAuthGate } from '../../auth/AuthGate';
import { FulfilmentSheet } from '../../fulfilment/components/FulfilmentSheet';
import { reservationLabel } from '../../fulfilment/format';
import { useMarketContext } from '../../market/MarketContext';
import { usePlaceOrder } from '../../orders/api/hooks';
import { useCart, useClearCart, useSetCartQuantity } from '../api/hooks';
import { usePlatform } from '../../legal/usePlatform';
import { CartRow } from '../components/CartRow';
import { CartSummary } from '../components/CartSummary';
import { cartCta } from '../model';


/** Warenkorb of one market: fulfilment bar, rows, summary, sticky CTA. */
export function MarketCartScreen({ navigation }: MarketTabScreenProps<'MarketCart'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { marketId } = useMarketContext();
  const { status } = useAuth();
  const { requireAuth } = useAuthGate();
  const query = useCart(marketId);
  const setQuantity = useSetCartQuantity(marketId);
  const clear = useClearCart(marketId);
  const placeOrder = usePlaceOrder(marketId);
  const [sheetOpen, setSheetOpen] = useState(false);
  // In Germany the order button must say that ordering obliges to pay (§ 312j BGB).
  const payOnSite = usePlatform().data?.payment === 'ON_SITE';

  if (status !== 'signedIn') {
    return (
      <EmptyState
        icon="basket"
        title={t('cart.signedOutTitle')}
        message={t('cart.signedOutMessage')}
        actionLabel={t('auth.signIn')}
        onAction={() => requireAuth('cart')}
      />
    );
  }
  const state = queryState(query);
  if (state !== null || query.data === undefined) {
    return state;
  }
  const cart = query.data;
  if (cart.items.length === 0) {
    return (
      <EmptyState
        icon="basket"
        title={t('cart.emptyTitle')}
        message={t('cart.emptyMessage')}
        actionLabel={t('cart.browse')}
        onAction={() => navigation.navigate('MarketHome')}
      />
    );
  }

  const cta = cartCta(cart);
  const reservation = cart.fulfilment ?? null;
  const barLabel =
    reservation !== null
      ? reservationLabel(t, reservation)
      : cart.market.fulfilment.delivery
        ? t('fulfilment.chooseDelivery')
        : t('fulfilment.choosePickup');

  const confirmClear = () =>
    Alert.alert(t('cart.clearTitle'), t('cart.clearMessage'), [
      { text: t('common.cancel'), style: 'cancel' },
      { text: t('cart.clear'), style: 'destructive', onPress: () => clear.mutate() },
    ]);

  const submit = () => {
    if (cta.kind === 'chooseSlot') {
      setSheetOpen(true);
      return;
    }
    placeOrder.mutate(undefined, {
      onSuccess: (order) => navigation.navigate('OrderConfirmation', { orderId: order.id }),
    });
  };

  const ctaTitle =
    cta.kind === 'chooseSlot'
      ? cta.fulfilment === 'DELIVERY'
        ? t('fulfilment.chooseDeliveryCta')
        : t('fulfilment.choosePickupCta')
      : t(payOnSite ? 'cart.orderCtaPayable' : 'cart.orderCta', { total: formatMoney(cta.total) });

  return (
    <Screen
      padded={false}
      footer={
        <View>
          {placeOrder.isError && (
            <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.error}>
              {t(errorMessageKey(placeOrder.error))}
            </AppText>
          )}
          <Button
            title={ctaTitle}
            onPress={submit}
            disabled={cta.kind === 'order' && !cta.enabled}
            loading={placeOrder.isPending}
            {...(cta.kind === 'order' && !cta.enabled ? { accessibilityHint: t('cart.minimumHint') } : {})}
            testID="cart-cta"
          />
        </View>
      }>
      <ScrollView contentContainerStyle={styles.content} testID="market-cart">
        <View style={styles.bar}>
          <Pressable
            onPress={() => setSheetOpen(true)}
            accessibilityRole="button"
            accessibilityLabel={forSpeech(barLabel)}
            accessibilityHint={t('fulfilment.linkHint')}
            style={styles.barButton}
            testID="fulfilment-bar">
            <Icon name="truck" size={26} color={colors.text} />
            <AppText variant="callout" dense center style={styles.flex} numberOfLines={1}>
              {barLabel}
            </AppText>
          </Pressable>
          <IconButton icon="more" label={t('cart.moreActions')} onPress={confirmClear} testID="cart-more" />
        </View>
        {cart.items.map((item) => (
          <CartRow
            key={item.productId}
            item={item}
            maxQuantity={item.maxOrderQuantity}
            busy={setQuantity.isPending && setQuantity.variables.productId === item.productId}
            onChangeQuantity={(productId, quantity) => setQuantity.mutate({ productId, quantity })}
          />
        ))}
        {setQuantity.isError && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert" style={styles.error}>
            {t(errorMessageKey(setQuantity.error))}
          </AppText>
        )}
        <CartSummary cart={cart} />
      </ScrollView>
      <FulfilmentSheet
        visible={sheetOpen}
        onClose={() => setSheetOpen(false)}
        market={cart.market}
        current={reservation !== null ? { type: reservation.type, slotId: reservation.slot.id, addressId: reservation.addressId ?? null } : null}
      />
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: t.colors.surfaceMuted,
    borderRadius: t.radii.tile,
    paddingStart: t.spacing.md,
    minHeight: 56,
    marginBottom: t.spacing.sm,
  },
  barButton: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm, minHeight: 52 },
  flex: { flex: 1 },
  error: { marginVertical: t.spacing.sm },
}));
