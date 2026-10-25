import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey } from '../../../shared/api';
import { formatDateTime } from '../../../shared/format';
import { makeStyles } from '../../../shared/theme';
import { AppText, Button, ToggleSwitch } from '../../../shared/ui';
import { settingsRequest, useOrderSettings, useUpdateOrderSettings } from '../api/hooks';

/** Next local 06:00: "no more orders today". */
function endOfDay(now: Date): Date {
  const next = new Date(now);
  next.setHours(6, 0, 0, 0);
  if (next <= now) {
    next.setDate(next.getDate() + 1);
  }
  return next;
}

/**
 * The switches a merchant needs on the go: online orders on or off, a pause and automatic
 * acceptance. Every other ordering setting (slots, holidays, delivery times, payment) is in the
 * merchant portal; customers only see the effect.
 */
export function OrderingControls({ marketId }: { marketId: string }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const query = useOrderSettings(marketId);
  const update = useUpdateOrderSettings(marketId);
  const settings = query.data;
  if (settings === undefined) {
    return null;
  }
  const change = (next: Parameters<typeof settingsRequest>[1]) => update.mutate(settingsRequest(settings, next));
  const paused = settings.pausedUntil != null && new Date(settings.pausedUntil) > new Date();
  return (
    <View style={styles.card} testID="ordering-controls">
      <AppText variant="headline">{t('merchant.ordering.title')}</AppText>
      <View style={styles.row}>
        <AppText variant="body" style={styles.flex}>
          {t('merchant.ordering.enabled')}
        </AppText>
        <ToggleSwitch
          value={settings.ordersEnabled}
          disabled={update.isPending}
          onValueChange={(ordersEnabled) => change({ ordersEnabled })}
          accessibilityLabel={t('merchant.ordering.enabled')}
          testID="ordering-enabled"
        />
      </View>
      <AppText variant="caption" color="textSecondary">
        {t(settings.ordersEnabled ? 'merchant.ordering.enabledHint' : 'merchant.ordering.disabledHint')}
      </AppText>
      {settings.ordersEnabled &&
        (paused ? (
          <View style={styles.stack}>
            <AppText variant="bodyStrong">{t('merchant.ordering.pausedUntil', { time: formatDateTime(settings.pausedUntil ?? '') })}</AppText>
            <Button title={t('merchant.ordering.endPause')} variant="outline" onPress={() => change({ pausedUntil: null })} loading={update.isPending} testID="ordering-end-pause" />
          </View>
        ) : (
          <View style={styles.buttons}>
            <Button
              title={t('merchant.ordering.pauseHour')}
              variant="outline"
              onPress={() => change({ pausedUntil: new Date(Date.now() + 60 * 60 * 1000).toISOString() })}
              disabled={update.isPending}
              style={styles.flex}
              testID="ordering-pause-hour"
            />
            <Button
              title={t('merchant.ordering.pauseToday')}
              variant="outline"
              onPress={() => change({ pausedUntil: endOfDay(new Date()).toISOString() })}
              disabled={update.isPending}
              style={styles.flex}
            />
          </View>
        ))}
      <View style={styles.row}>
        <AppText variant="body" style={styles.flex}>
          {t('merchant.ordering.autoAccept')}
        </AppText>
        <ToggleSwitch
          value={settings.autoAccept}
          disabled={update.isPending}
          onValueChange={(autoAccept) => change({ autoAccept })}
          accessibilityLabel={t('merchant.ordering.autoAccept')}
        />
      </View>
      {update.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert">
          {t(errorMessageKey(update.error))}
        </AppText>
      )}
      <AppText variant="caption" color="textSecondary">
        {t('merchant.ordering.more')}
      </AppText>
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  card: { backgroundColor: t.colors.surfaceRose, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: t.spacing.sm, marginBottom: t.spacing.md },
  row: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  buttons: { flexDirection: 'row', gap: t.spacing.sm },
  stack: { gap: t.spacing.sm },
  flex: { flex: 1 },
}));
