import { View } from 'react-native';

import { makeStyles } from '../theme';

/**
 * Progress from the start edge; in RTL it fills from the right (right-to-left.md › Controls:
 * flip controls that show progress).
 */
export function ProgressBar({ ratio, accessibilityLabel }: { ratio: number; accessibilityLabel: string }) {
  const styles = useStyles();
  const clamped = Math.max(0, Math.min(1, ratio));
  return (
    <View
      style={styles.track}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(clamped * 100) }}>
      <View style={[styles.fill, { width: `${clamped * 100}%` }]} />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  track: { height: 8, borderRadius: 4, backgroundColor: t.colors.surfaceMuted, overflow: 'hidden' },
  fill: { height: 8, borderRadius: 4, backgroundColor: t.colors.primary },
}));
