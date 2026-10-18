import type { ReactNode } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '../api/errorMessages';
import { isOfflineError } from '../api/ApiError';
import { colors, spacing, typography } from '../theme';
import { Button } from './Button';

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <View style={styles.center} accessible accessibilityLabel={label} accessibilityRole="progressbar">
      <ActivityIndicator size="large" color={colors.primary} />
    </View>
  );
}

interface EmptyStateProps {
  title: string;
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function EmptyState({ title, message, actionLabel, onAction }: EmptyStateProps) {
  return (
    <View style={styles.center}>
      <Text style={styles.title} accessibilityRole="header">
        {title}
      </Text>
      {message !== undefined && <Text style={styles.message}>{message}</Text>}
      {actionLabel !== undefined && onAction !== undefined && (
        <Button title={actionLabel} onPress={onAction} style={styles.action} />
      )}
    </View>
  );
}

interface ErrorStateProps {
  error: unknown;
  onRetry?: () => void;
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const offline = isOfflineError(error);
  return (
    <View style={styles.center} accessibilityLiveRegion="polite">
      <Text style={styles.title} accessibilityRole="header">
        {offline ? 'You are offline' : 'Something went wrong'}
      </Text>
      <Text style={styles.message}>{errorMessage(error)}</Text>
      {onRetry !== undefined && (
        <Button
          title="Try again"
          onPress={onRetry}
          style={styles.action}
          accessibilityHint="Reloads this content"
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

/**
 * Renders the loading or error state of a query, or `null` when data is available.
 * Usage: `const state = queryState(query); if (state) return state;`
 */
export function queryState(query: QueryLike): ReactNode {
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  }
  return null;
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  title: { ...typography.heading, color: colors.text, textAlign: 'center' },
  message: { ...typography.body, color: colors.textMuted, textAlign: 'center', marginTop: spacing.sm },
  action: { marginTop: spacing.lg, alignSelf: 'stretch' },
});
