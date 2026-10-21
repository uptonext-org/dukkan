import { FlatList, RefreshControl, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { openMarketCart } from '../../../navigation/marketLinks';
import type { ShellTabScreenProps } from '../../../navigation/types';
import { formatMoney, formatMoneyForAccessibility } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, EmptyState, ImageTile, ListRow, queryState } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';
import { useAuthGate } from '../../auth/AuthGate';
import { ShellHeader } from '../../marketplace/components/ShellHeader';
import { useCarts } from '../api/hooks';

/** Warenkörbe: one row per non-empty cart; tapping opens that market's cart tab. */
export function CartsScreen({ navigation }: ShellTabScreenProps<'Carts'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { status } = useAuth();
  const { requireAuth } = useAuthGate();
  const query = useCarts();

  let body;
  if (status !== 'signedIn') {
    body = (
      <EmptyState
        icon="basket"
        title={t('carts.signedOutTitle')}
        message={t('carts.signedOutMessage')}
        actionLabel={t('auth.signIn')}
        onAction={() => requireAuth('cart')}
      />
    );
  } else {
    body =
      queryState(query) ??
      (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(c) => c.cartId}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={query.isRefetching} onRefresh={() => void query.refetch()} tintColor={colors.primary} />}
          ListEmptyComponent={
            <EmptyState icon="basket" title={t('carts.emptyTitle')} message={t('carts.emptyMessage')} actionLabel={t('carts.discover')} onAction={() => navigation.navigate('Discover')} />
          }
          ItemSeparatorComponent={RowSeparator}
          renderItem={({ item }) => (
            <ListRow
              title={item.market.name}
              subtitle={t('carts.items', { count: item.itemCount })}
              leading={<ImageTile uri={item.market.logoUrl} size={48} radius={24} tint="sand" placeholderIcon="store" />}
              trailing={
                <AppText variant="headline" dense>
                  {formatMoney(item.total)}
                </AppText>
              }
              accessibilityLabel={`${item.market.name}, ${t('carts.items', { count: item.itemCount })}, ${formatMoneyForAccessibility(item.total)}`}
              accessibilityHint={t('carts.openHint')}
              onPress={() => openMarketCart(navigation, item.market.id)}
              testID={`cart-summary-${item.market.id}`}
            />
          )}
        />
      );
  }
  return (
    <View style={styles.root}>
      <ShellHeader title={t('carts.title')} />
      {body}
    </View>
  );
}

function RowSeparator() {
  const { colors } = useTheme();
  return <View style={{ height: 1, backgroundColor: colors.separator }} />;
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  list: { paddingHorizontal: t.layout.gutter, flexGrow: 1 },
}));
