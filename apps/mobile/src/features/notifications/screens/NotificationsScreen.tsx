import { useEffect } from 'react';
import { FlatList, Pressable, RefreshControl, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import type { AppNotification } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { errorMessageKey } from '../../../shared/api';
import { AppText, EmptyState, Screen, ScreenHeader, ToggleSwitch, queryState } from '../../../shared/ui';
import { useMarkNotificationsRead, useNotificationSettings, useNotifications, useUpdateNotificationSettings } from '../api/hooks';

const MERCHANT_KINDS = new Set(['NEW_ORDER', 'ORDER_CANCELLED']);

/** Profil → "Mitteilungen": order updates for customers and merchants. Opening it marks all as read. */
export function NotificationsScreen({ navigation }: RootStackScreenProps<'Notifications'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const query = useNotifications();
  const markRead = useMarkNotificationsRead();
  const unread = query.data?.unreadCount ?? 0;

  useEffect(() => {
    if (unread > 0 && !markRead.isPending) {
      markRead.mutate();
    }
    // Only when new unread entries arrive.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unread]);

  const open = (n: AppNotification) => {
    if (MERCHANT_KINDS.has(n.kind) && n.marketId != null) {
      navigation.navigate('MerchantOrders', { marketId: n.marketId });
    } else if (n.orderId != null) {
      navigation.navigate('OrderDetail', { orderId: n.orderId });
    }
  };

  return (
    <Screen padded={false} header={<ScreenHeader title={t('notifications.title')} onBack={() => navigation.goBack()} />}>
      {queryState(query) ?? (
        <FlatList
          data={query.data?.items ?? []}
          keyExtractor={(n) => n.id}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={query.isRefetching} onRefresh={() => void query.refetch()} tintColor={colors.primary} />}
          ListHeaderComponent={<PushSetting />}
          ListEmptyComponent={<EmptyState icon="bell" title={t('notifications.emptyTitle')} message={t('notifications.emptyMessage')} />}
          renderItem={({ item }) => (
            <Pressable
              onPress={() => open(item)}
              accessibilityRole="button"
              accessibilityLabel={`${item.read ? '' : `${t('notifications.unread')}, `}${item.title}, ${item.body}`}
              style={styles.row}
              testID={`notification-${item.id}`}>
              <View style={[styles.dot, { backgroundColor: item.read ? 'transparent' : colors.accentFill }]} />
              <View style={styles.texts}>
                <AppText variant={item.read ? 'body' : 'bodyStrong'} dense>
                  {item.title}
                </AppText>
                <AppText variant="subhead" color="textSecondary" dense>
                  {item.body}
                </AppText>
                <AppText variant="caption" color="textSecondary">
                  {formatDateTime(item.createdAt)}
                </AppText>
              </View>
            </Pressable>
          )}
        />
      )}
    </Screen>
  );
}

/** "Push-Mitteilungen" switch; the inbox fills either way. */
function PushSetting() {
  const styles = useStyles();
  const { t } = useTranslation();
  const settings = useNotificationSettings();
  const update = useUpdateNotificationSettings();
  if (settings.data === undefined) {
    return null;
  }
  return (
    <View style={styles.setting}>
      <View style={styles.texts}>
        <AppText variant="bodyStrong" dense>
          {t('notifications.push')}
        </AppText>
        <AppText variant="caption" color="textSecondary">
          {t('notifications.pushHint')}
        </AppText>
        {update.isError && (
          <AppText variant="caption" color="accent" accessibilityRole="alert">
            {t(errorMessageKey(update.error))}
          </AppText>
        )}
      </View>
      <ToggleSwitch
        accessibilityLabel={t('notifications.push')}
        value={settings.data.pushEnabled}
        onValueChange={(pushEnabled) => update.mutate({ pushEnabled })}
        testID="push-setting"
      />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  list: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.xxxl, flexGrow: 1 },
  row: { flexDirection: 'row', gap: t.spacing.sm, paddingVertical: t.spacing.md, borderBottomWidth: 1, borderBottomColor: t.colors.separator, minHeight: 64 },
  dot: { width: 10, height: 10, borderRadius: 5, marginTop: 6 },
  texts: { flex: 1, gap: 2 },
  setting: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, paddingVertical: t.spacing.md, borderBottomWidth: 1, borderBottomColor: t.colors.separator },
}));
