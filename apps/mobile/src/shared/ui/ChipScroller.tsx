import type { ReactNode } from 'react';
import { ScrollView, type StyleProp, type ViewStyle } from 'react-native';

import { forSpeech } from '../format/bidi';
import { makeStyles } from '../theme';

interface ChipScrollerProps {
  children: ReactNode;
  accessibilityLabel?: string;
  testID?: string;
  /** Outer margins of the row. */
  style?: StyleProp<ViewStyle>;
  /** Inset of the first and last chip; the row itself bleeds to the screen edges. Default: gutter. */
  inset?: number;
}

/**
 * A horizontally scrolling row of chips. It never shrinks (`flexShrink: 0`, `flexGrow: 0`): a
 * horizontal ScrollView in a column with a list below it would otherwise be squashed below the
 * 44 pt chip height. It scrolls edge to edge; the gutter is content padding, so chips are not
 * clipped at the gutter. Starts at the start edge in RTL.
 */
export function ChipScroller({ children, accessibilityLabel, testID, style, inset }: ChipScrollerProps) {
  const styles = useStyles();
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={[styles.row, style]}
      contentContainerStyle={[styles.content, inset !== undefined && { paddingHorizontal: inset }]}
      accessibilityLabel={forSpeech(accessibilityLabel)}
      keyboardShouldPersistTaps="handled"
      testID={testID}>
      {children}
    </ScrollView>
  );
}

const useStyles = makeStyles((t) => ({
  row: { flexGrow: 0, flexShrink: 0, minHeight: t.touchTarget },
  content: { gap: t.spacing.sm, paddingHorizontal: t.layout.gutter, alignItems: 'center' },
}));
