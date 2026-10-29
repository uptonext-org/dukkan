import { useState } from 'react';
import { FlatList, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey } from '../../../shared/api';
import type { MarketStatus, MerchantMarket } from '../../../shared/api/types';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, ChipScroller, Chip, EmptyState, ImageTile, Screen, ScreenHeader, queryState } from '../../../shared/ui';
import { useAdminMarkets, useSetMarketStatus } from '../api/hooks';

const STATUSES: MarketStatus[] = ['PENDING_REVIEW', 'ACTIVE', 'SUSPENDED'];

/**
 * Market review for admins (B-36), in the app itself: approve new markets, suspend them or return
 * them to review. Approval needs complete seller information; the checklist shows what is missing.
 */
export function AdminMarketsScreen({ navigation }: RootStackScreenProps<'AdminMarkets'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const [status, setStatus] = useState<MarketStatus | null>('PENDING_REVIEW');
  const query = useAdminMarkets(status);
  const setMarketStatus = useSetMarketStatus();

  return (
    <Screen padded={false} header={<ScreenHeader title={t('admin.title')} onBack={() => navigation.goBack()} />}>
      <View style={styles.filters} accessibilityLabel={t('admin.filterLabel')}>
        <ChipScroller>
          {STATUSES.map((s) => (
            <Chip key={s} label={t(`merchant.status.${s}`)} selected={status === s} onPress={() => setStatus(s)} testID={`admin-filter-${s}`} />
          ))}
          <Chip label={t('admin.all')} selected={status === null} onPress={() => setStatus(null)} testID="admin-filter-all" />
        </ChipScroller>
      </View>
      {setMarketStatus.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert" style={styles.error}>
          {t(errorMessageKey(setMarketStatus.error))}
        </AppText>
      )}
      {queryState(query) ?? (
        <FlatList
          data={query.data ?? []}
          keyExtractor={(m) => m.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<EmptyState icon="store" title={t('admin.empty')} />}
          renderItem={({ item }) => (
            <MarketCard
              market={item}
              busy={setMarketStatus.isPending}
              onSetStatus={(next) => setMarketStatus.mutate({ marketId: item.id, status: next })}
            />
          )}
        />
      )}
    </Screen>
  );
}

function MarketCard({ market, busy, onSetStatus }: { market: MerchantMarket; busy: boolean; onSetStatus: (status: MarketStatus) => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const incomplete = market.review?.complete === false;
  return (
    <View style={styles.card} accessibilityLabel={market.name} testID={`admin-market-${market.id}`}>
      <View style={styles.cardHeader}>
        <ImageTile uri={market.logoUrl} size={48} radius={24} tint="sand" placeholderIcon="store" />
        <View style={styles.cardTitle}>
          <AppText variant="headline">{market.name}</AppText>
          <AppText variant="caption" color="textSecondary">
            {`${market.type.name} · ${t(`merchant.status.${market.status}`)}`}
          </AppText>
        </View>
      </View>
      <AppText variant="subhead" color="textSecondary">
        {market.addressLine}
      </AppText>
      <AppText variant="subhead">
        {`${t('admin.legal')}: ${market.imprint.legalName}`}
        {market.phone != null && market.phone !== '' ? ` · ${t('admin.phone')}: ${market.phone}` : ''}
      </AppText>
      {market.review && (
        <View style={styles.checklist} accessibilityLabel={t('admin.checklist')}>
          {market.review.items.map((item) => (
            <AppText key={item.code} variant="caption" color={item.ok ? 'primary' : item.required ? 'accent' : 'textSecondary'}>
              {`${item.ok ? '✓' : '✗'} ${t(`admin.check.${item.code}`, { defaultValue: item.code })}${item.required ? '' : ` (${t('admin.optional')})`}`}
            </AppText>
          ))}
        </View>
      )}
      {incomplete && (
        <AppText variant="caption" color="accent">
          {t('admin.incomplete')}
        </AppText>
      )}
      <View style={styles.actions}>
        {market.status !== 'ACTIVE' && (
          <Button
            title={t('admin.approve')}
            icon="check"
            disabled={busy || incomplete}
            onPress={() => onSetStatus('ACTIVE')}
            testID={`admin-approve-${market.id}`}
          />
        )}
        {market.status !== 'SUSPENDED' && (
          <Button title={t('admin.suspend')} variant="danger" disabled={busy} onPress={() => onSetStatus('SUSPENDED')} testID={`admin-suspend-${market.id}`} />
        )}
        {market.status !== 'PENDING_REVIEW' && (
          <Button title={t('admin.backToReview')} variant="outline" disabled={busy} onPress={() => onSetStatus('PENDING_REVIEW')} testID={`admin-review-${market.id}`} />
        )}
      </View>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  filters: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.md },
  error: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.sm },
  list: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl, gap: t.spacing.md, flexGrow: 1 },
  card: { backgroundColor: t.colors.surfaceMuted, borderRadius: 16, padding: t.spacing.lg, gap: t.spacing.sm },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  cardTitle: { flex: 1, gap: 2 },
  checklist: { gap: 2, paddingTop: t.spacing.xs },
  actions: { gap: t.spacing.sm, paddingTop: t.spacing.sm },
}));
