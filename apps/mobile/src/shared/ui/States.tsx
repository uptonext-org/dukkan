import { useEffect, useState, type ReactNode } from 'react';
import { ActivityIndicator, Animated, View, type DimensionValue } from 'react-native';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../format/bidi';
import { errorMessageKey } from '../api/errorMessages';
import { isOfflineError } from '../api/ApiError';
import { useReduceMotion } from '../hooks/useReduceMotion';
import { makeStyles, useTheme } from '../theme';
import { AppText } from './AppText';
import { Button } from './Button';
import { Icon, type IconName } from './Icon';

export function LoadingState({ label }: { label?: string }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  return (
    <View style={styles.center} accessible accessibilityLabel={forSpeech(label ?? t('common.loading'))} accessibilityRole="progressbar">
      <ActivityIndicator size="large" color={colors.primary} />
    </View>
  );
}

/** Placeholder block that pulses softly (static when Reduce Motion is on). */
export function Skeleton({ width = '100%', height, radius = 12 }: { width?: DimensionValue; height: number; radius?: number }) {
  const { colors } = useTheme();
  const reduceMotion = useReduceMotion();
  const [opacity] = useState(() => new Animated.Value(1));
  useEffect(() => {
    if (reduceMotion) {
      return undefined;
    }
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.5, duration: 700, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 1, duration: 700, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [opacity, reduceMotion]);
  return (
    <Animated.View
      style={{ width, height, borderRadius: radius, backgroundColor: colors.surfaceMuted, opacity }}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    />
  );
}

/** Skeleton list of cards used while the first page loads. */
export function CardSkeletons({ count = 3, height = 220 }: { count?: number; height?: number }) {
  const styles = useStyles();
  const { t } = useTranslation();
  return (
    <View style={styles.skeletons} accessible accessibilityRole="progressbar" accessibilityLabel={t('common.loading')}>
      {Array.from({ length: count }, (_, i) => (
        <View key={i} style={styles.skeletonCard}>
          <Skeleton height={height * 0.6} radius={16} />
          <Skeleton height={18} width="60%" />
          <Skeleton height={14} width="40%" />
        </View>
      ))}
    </View>
  );
}

interface EmptyStateProps {
  icon?: IconName;
  title: string;
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ icon, title, message, actionLabel, onAction }: EmptyStateProps) {
  const styles = useStyles();
  const { colors } = useTheme();
  return (
    <View style={styles.center}>
      {icon !== undefined && (
        <View style={styles.iconCircle}>
          <Icon name={icon} size={32} color={colors.primary} />
        </View>
      )}
      <AppText variant="headline" center accessibilityRole="header">
        {title}
      </AppText>
      {message !== undefined && (
        <AppText variant="callout" color="textSecondary" center style={styles.message}>
          {message}
        </AppText>
      )}
      {actionLabel !== undefined && onAction !== undefined && (
        <Button title={actionLabel} onPress={onAction} style={styles.action} />
      )}
    </View>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const offline = isOfflineError(error);
  return (
    <View style={styles.center} accessibilityLiveRegion="polite">
      <View style={styles.iconCircle}>
        <Icon name={offline ? 'offline' : 'alert'} size={32} color={colors.accent} />
      </View>
      <AppText variant="headline" center accessibilityRole="header">
        {offline ? t('states.offlineTitle') : t('states.errorTitle')}
      </AppText>
      <AppText variant="callout" color="textSecondary" center style={styles.message}>
        {t(errorMessageKey(error))}
      </AppText>
      {onRetry !== undefined && (
        <Button
          title={t('common.retry')}
          onPress={onRetry}
          variant="secondary"
          style={styles.action}
          accessibilityHint={t('common.retryHint')}
        />
      )}
    </View>
  );
}

interface QueryLike {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => unknown;
}

/** Loading or error UI for a query, or `null` when data is available. */
export function queryState(query: QueryLike, loading?: ReactNode): ReactNode {
  if (query.isPending) {
    return loading ?? <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  }
  return null;
}

const useStyles = makeStyles((t) => ({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: t.spacing.xxl, gap: t.spacing.sm },
  iconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: t.colors.surfaceMuted,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: t.spacing.sm,
  },
  message: { marginTop: t.spacing.xs },
  action: { marginTop: t.spacing.lg, alignSelf: 'stretch' },
  skeletons: { padding: t.layout.gutter, gap: t.spacing.xxl },
  skeletonCard: { gap: t.spacing.sm },
}));
