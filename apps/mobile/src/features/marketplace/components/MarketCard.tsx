import { memo } from 'react';
import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../../../shared/format';
import type { MarketSummary } from '../../../shared/api';
import { makeStyles, tintForKey, useTheme } from '../../../shared/theme';
import { AccentBadge, AppText, Chip, ImageTile } from '../../../shared/ui';
import { deliveryFeeLabel, distanceLabel, minimumOrderLabel, openingLabel, typeAndCuisine } from '../format';

interface MarketCardProps {
  market: MarketSummary;
  onPress: (market: MarketSummary) => void;
  compact?: boolean;
}

/**
 * Market card (decision 22, design-system.md §4.2): 16:9 cover with the logo circle over the
 * bottom-start corner, name, type · cuisine, distance · open/closed, delivery/pickup chips,
 * minimum order and delivery fee, and a red offers badge.
 */
function MarketCardComponent({ market, onPress, compact = false }: MarketCardProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const distance = distanceLabel(market);
  const opening = openingLabel(t, market);
  const fee = deliveryFeeLabel(t, market);
  const minimum = minimumOrderLabel(t, market);
  const offers = market.activePromotionCount > 0 ? t('market.offers', { count: market.activePromotionCount }) : undefined;
  const statusLine = [distance, opening].filter(Boolean).join(' · ');
  const spoken = [
    market.name,
    typeAndCuisine(market),
    statusLine,
    market.fulfilment.delivery ? t('market.delivery') : undefined,
    market.fulfilment.pickup ? t('market.pickup') : undefined,
    minimum,
    fee,
    offers,
  ]
    .filter(Boolean)
    .join(', ');

  return (
    <Pressable
      onPress={() => onPress(market)}
      accessibilityRole="button"
      accessibilityLabel={forSpeech(spoken)}
      accessibilityHint={t('market.openHint')}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
      testID={`market-card-${market.id}`}>
      {!compact && (
        <View>
          <ImageTile uri={market.coverImageUrl} aspectRatio={16 / 9} tint={tintForKey(market.id)} placeholderIcon="store" />
          <View style={styles.logo} testID="market-logo">
            <ImageTile uri={market.logoUrl} size={52} radius={26} tint="sand" placeholderIcon="store" />
          </View>
          {offers !== undefined && (
            <View style={styles.badge}>
              <AccentBadge label={offers} />
            </View>
          )}
        </View>
      )}
      <View style={[styles.body, compact && styles.bodyCompact]}>
        <View style={styles.titleRow}>
          {compact && <ImageTile uri={market.logoUrl} size={40} radius={20} tint="sand" placeholderIcon="store" />}
          <View style={styles.flex}>
            <AppText variant="headline" dense numberOfLines={1}>
              {market.name}
            </AppText>
            <AppText variant="subhead" color="textSecondary" dense numberOfLines={1}>
              {typeAndCuisine(market)}
            </AppText>
          </View>
          {compact && offers !== undefined && <AccentBadge label={offers} />}
        </View>
        <AppText variant="subhead" dense style={{ color: market.openNow ? colors.primary : colors.textSecondary }}>
          {statusLine}
        </AppText>
        <View style={styles.chips}>
          {market.fulfilment.delivery && <Chip label={t('market.delivery')} icon="truck" />}
          {market.fulfilment.pickup && <Chip label={t('market.pickup')} icon="store" />}
        </View>
        {(minimum !== undefined || fee !== undefined) && (
          <AppText variant="subhead" color="textSecondary" dense>
            {[minimum, fee].filter(Boolean).join(' · ')}
          </AppText>
        )}
      </View>
    </Pressable>
  );
}

export const MarketCard = memo(MarketCardComponent);

const useStyles = makeStyles((t) => ({
  card: { gap: t.spacing.sm },
  pressed: { opacity: 0.85 },
  logo: {
    position: 'absolute',
    start: t.spacing.md,
    bottom: -20,
    borderRadius: 30,
    borderWidth: 3,
    borderColor: t.colors.bg,
    backgroundColor: t.colors.bg,
  },
  badge: { position: 'absolute', top: t.spacing.md, end: t.spacing.md },
  body: { gap: 4, paddingTop: t.spacing.lg },
  bodyCompact: { paddingTop: 0 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
  flex: { flex: 1 },
  chips: { flexDirection: 'row', gap: t.spacing.sm, flexWrap: 'wrap' },
}));
