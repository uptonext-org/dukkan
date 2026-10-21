import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';

import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, IconButton, ImageTile } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';
import { useFavoriteMarkets, useToggleMarketFavorite } from '../../favorites/api/hooks';
import { useMarket } from '../api/hooks';
import { useMarketContext } from '../MarketContext';

/** Sub-app header: back to the marketplace (start), logo + name, favourite heart (end). */
export function MarketHeader() {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { marketId, exit } = useMarketContext();
  const { data: market } = useMarket(marketId);
  const { status } = useAuth();
  const { data: favorites } = useFavoriteMarkets();
  const { toggle } = useToggleMarketFavorite();
  const favorite = status === 'signedIn' && (favorites ?? []).some((m) => m.id === marketId);
  const name = market?.name ?? '';
  return (
    <View style={[styles.header, { paddingTop: insets.top + 2 }]}>
      <IconButton icon="back" label={t('market.backToMarketplace')} onPress={exit} testID="market-exit" />
      <View style={styles.identity} accessible accessibilityRole="header" accessibilityLabel={name}>
        <ImageTile uri={market?.logoUrl} size={32} radius={16} tint="sand" placeholderIcon="store" />
        <AppText variant="headline" dense numberOfLines={1} style={styles.name}>
          {name}
        </AppText>
      </View>
      <IconButton
        icon="heart"
        filled={favorite}
        color={favorite ? colors.accent : colors.text}
        selected={favorite}
        label={favorite ? t('market.unfavorite', { name }) : t('market.favorite', { name })}
        onPress={() => toggle(marketId, favorite)}
        testID="market-favorite"
      />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: t.spacing.xs,
    paddingBottom: t.spacing.xs,
    backgroundColor: t.colors.bg,
  },
  identity: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: t.spacing.sm },
  name: { flexShrink: 1 },
}));
