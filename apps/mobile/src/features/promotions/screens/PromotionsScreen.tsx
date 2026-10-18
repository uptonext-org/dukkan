import { memo, useCallback } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View, type ListRenderItem } from 'react-native';

import type { Promotion } from '../../../shared/api';
import { formatDate } from '../../../shared/format/dates';
import { colors, radii, spacing, touchTarget, typography } from '../../../shared/theme';
import { Badge, EmptyState, Screen, queryState } from '../../../shared/ui';
import type { RootStackScreenProps } from '../../../navigation/types';
import { usePromotions } from '../api/hooks';
import { describePromotion } from '../model';

const PromotionCard = memo(function PromotionCard({
  promotion,
  onOpen,
}: {
  promotion: Promotion;
  onOpen: (promotion: Promotion) => void;
}) {
  const mechanic = describePromotion(promotion);
  const count = promotion.productIds.length;
  return (
    <Pressable
      onPress={() => onOpen(promotion)}
      accessibilityRole="button"
      accessibilityLabel={`${promotion.title}, ${mechanic}, valid until ${formatDate(promotion.validUntil)}`}
      accessibilityHint={count === 1 ? 'Opens the product' : 'Shows all products on offer'}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
      <View style={styles.cardHeader}>
        <Text style={styles.title}>{promotion.title}</Text>
        <Badge label={mechanic} tone="promo" />
      </View>
      {promotion.description != null && <Text style={styles.body}>{promotion.description}</Text>}
      <Text style={styles.meta}>
        Valid {formatDate(promotion.validFrom)} – {formatDate(promotion.validUntil)} · {count}{' '}
        {count === 1 ? 'product' : 'products'}
      </Text>
    </Pressable>
  );
});

export function PromotionsScreen({ navigation }: RootStackScreenProps<'Promotions'>) {
  const promotions = usePromotions();

  const open = useCallback(
    (promotion: Promotion) => {
      const [only] = promotion.productIds;
      if (promotion.productIds.length === 1 && only !== undefined) {
        navigation.navigate('ProductDetail', { productId: only });
      } else {
        navigation.navigate('Tabs', { screen: 'Search', params: { promotedOnly: true } });
      }
    },
    [navigation],
  );
  const renderItem = useCallback<ListRenderItem<Promotion>>(
    ({ item }) => <PromotionCard promotion={item} onOpen={open} />,
    [open],
  );

  const state = queryState(promotions);
  if (state) {
    return <Screen padded={false}>{state}</Screen>;
  }
  return (
    <Screen padded={false}>
      <FlatList
        data={promotions.data}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        contentContainerStyle={styles.list}
        refreshing={promotions.isRefetching}
        onRefresh={() => void promotions.refetch()}
        ListEmptyComponent={
          <EmptyState title="No offers right now" message="New offers are added every week." />
        }
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { padding: spacing.lg, gap: spacing.md, flexGrow: 1 },
  card: {
    minHeight: touchTarget,
    padding: spacing.lg,
    borderRadius: radii.lg,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    gap: spacing.xs,
  },
  pressed: { backgroundColor: colors.primaryMuted },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: spacing.sm,
  },
  title: { ...typography.bodyStrong, color: colors.text, flex: 1 },
  body: { ...typography.body, color: colors.text },
  meta: { ...typography.caption, color: colors.textMuted },
});
