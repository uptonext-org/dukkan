import { FlatList, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, flattenPages } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, EmptyState, Screen, ScreenHeader, ToggleSwitch, queryState } from '../../../shared/ui';
import { useMerchantFeedback, useSetFeedbackEnabled } from '../api/hooks';

/** Customer feedback to my market: switch receiving it on or off and read the messages. */
export function FeedbackScreen({ route, navigation }: RootStackScreenProps<'MerchantFeedback'>) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { marketId } = route.params;
  const query = useMerchantFeedback(marketId);
  const toggle = useSetFeedbackEnabled(marketId);
  const enabled = toggle.isPending ? toggle.variables : (query.data?.pages[0]?.enabled ?? false);
  const items = flattenPages(query.data ? { pages: query.data.pages.map((p) => p.feedback) } : undefined);

  return (
    <Screen padded={false} header={<ScreenHeader title={t('merchantFeedback.title')} onBack={() => navigation.goBack()} />}>
      {queryState(query) ?? (
        <FlatList
          data={items}
          keyExtractor={(f) => f.id}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.header}>
              <View style={styles.switchRow}>
                <AppText variant="body" style={styles.flex}>
                  {t('merchantFeedback.toggle')}
                </AppText>
                <ToggleSwitch value={enabled} onValueChange={(v) => toggle.mutate(v)} accessibilityLabel={t('merchantFeedback.toggle')} testID="feedback-enabled" />
              </View>
              <AppText variant="caption" color="textSecondary">
                {t('merchantFeedback.toggleHint')}
              </AppText>
              {toggle.isError && (
                <AppText variant="caption" color="accent" accessibilityRole="alert">
                  {t(errorMessageKey(toggle.error))}
                </AppText>
              )}
            </View>
          }
          ListEmptyComponent={<EmptyState icon="message" title={t('merchantFeedback.emptyTitle')} message={t('merchantFeedback.emptyMessage')} />}
          renderItem={({ item }) => (
            <View style={styles.row} testID={`feedback-${item.id}`}>
              <AppText variant="subhead" color="textSecondary">
                {[item.customerName, formatDateTime(item.createdAt), item.rating != null ? t('merchantFeedback.rating', { n: item.rating }) : null].filter(Boolean).join(' · ')}
              </AppText>
              <AppText variant="body">{item.message}</AppText>
            </View>
          )}
          ListFooterComponent={
            query.hasNextPage ? (
              <Button title={t('merchantFeedback.more')} variant="ghost" onPress={() => void query.fetchNextPage()} loading={query.isFetchingNextPage} />
            ) : undefined
          }
        />
      )}
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  list: { padding: t.layout.gutter, paddingBottom: t.spacing.xxxl },
  header: { gap: t.spacing.xs, marginBottom: t.spacing.lg },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: t.spacing.md },
  flex: { flex: 1 },
  row: { paddingVertical: t.spacing.md, borderBottomWidth: 1, borderBottomColor: t.colors.separator, gap: t.spacing.xs },
}));
