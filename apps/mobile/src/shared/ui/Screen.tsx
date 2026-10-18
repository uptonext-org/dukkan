import type { ReactNode } from 'react';
import { ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { colors, spacing } from '../theme';

interface ScreenProps {
  children: ReactNode;
  /** Wrap content in a ScrollView (forms, detail pages). Lists should pass `false`. */
  scroll?: boolean;
  padded?: boolean;
  style?: StyleProp<ViewStyle>;
  footer?: ReactNode;
}

export function Screen({ children, scroll = false, padded = true, style, footer }: ScreenProps) {
  const contentStyle = [padded && styles.padded, style];
  return (
    <View style={styles.root}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={contentStyle}
          keyboardShouldPersistTaps="handled"
          contentInsetAdjustmentBehavior="automatic">
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.fill, contentStyle]}>{children}</View>
      )}
      {footer !== undefined && <View style={styles.footer}>{footer}</View>}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  fill: { flex: 1 },
  padded: { padding: spacing.lg },
  footer: {
    padding: spacing.lg,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    backgroundColor: colors.surface,
  },
});
