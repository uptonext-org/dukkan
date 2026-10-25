import { ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { makeStyles } from '../../../shared/theme';
import { AppText, Button, ErrorState, LoadingState, ProductTile } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';
import { useAuthGate } from '../../auth/AuthGate';
import { useProductActions } from '../../catalog/useProductActions';
import { useMarketContext } from '../../market/MarketContext';
import { useMarketFavorites } from '../api/hooks';
import { FavoritesIllustration } from '../components/FavoritesIllustration';

/** Favoriten: big title, explanation, illustration, then favourites grouped by category. */
export function MarketFavoritesScreen() {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = useMarketContext();
  const { status } = useAuth();
  const { requireAuth } = useAuthGate();
  const query = useMarketFavorites(marketId);
  const actions = useProductActions(marketId);

  return (
    <ScrollView contentContainerStyle={styles.content} testID="market-favorites">
      <AppText variant="largeTitle" accessibilityRole="header">
        {t('favorites.title')}
      </AppText>
      <AppText variant="body" color="textSecondary">
        {t('favorites.explanation')}
      </AppText>
      <View style={styles.illustration}>
        <FavoritesIllustration />
      </View>
      {status !== 'signedIn' ? (
        <View style={styles.signIn}>
          <AppText variant="callout" center>
            {t('favorites.signInPrompt')}
          </AppText>
          <Button title={t('auth.signIn')} onPress={() => requireAuth('favorites')} />
        </View>
      ) : query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.length === 0 ? (
        <AppText variant="callout" color="textSecondary" center>
          {t('favorites.empty')}
        </AppText>
      ) : (
        query.data.map((group) => (
          <View key={group.category.id} style={styles.group}>
            <AppText variant="title" accessibilityRole="header">
              {group.category.name}
            </AppText>
            <View style={styles.grid}>
              {group.items.map((item) => (
                <View key={item.id} style={styles.cell}>
                  <ProductTile
                    product={item}
                    quantity={actions.quantities.get(item.id) ?? 0}
                    busy={actions.busyProductId === item.id}
            orderable={actions.orderable}
                    onPress={actions.openProduct}
                    onChangeQuantity={actions.changeQuantity}
                    onToggleFavorite={actions.toggleFavorite}
                  />
                  {item.favoriteReason === 'FREQUENTLY_ORDERED' && (
                    <AppText variant="caption" color="textSecondary" dense>
                      {t('favorites.frequentlyOrdered')}
                    </AppText>
                  )}
                </View>
              ))}
            </View>
          </View>
        ))
      )}
    </ScrollView>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, gap: t.spacing.md, paddingBottom: t.spacing.xxxl },
  illustration: { alignItems: 'center', marginVertical: t.spacing.lg },
  signIn: { gap: t.spacing.md },
  group: { gap: t.spacing.md, marginTop: t.spacing.lg },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: t.spacing.md, rowGap: t.spacing.xxl },
  cell: { width: '47%', flexGrow: 1, maxWidth: '50%', gap: 4 },
}));
