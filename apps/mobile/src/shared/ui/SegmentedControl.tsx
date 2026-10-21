import { Pressable, View } from 'react-native';

import { forSpeech } from '../format/bidi';
import { makeStyles } from '../theme';
import { AppText } from './AppText';
import { Icon, type IconName } from './Icon';

interface Segment<T extends string> {
  value: T;
  label: string;
  icon?: IconName;
}

interface SegmentedControlProps<T extends string> {
  segments: readonly Segment<T>[];
  value: T;
  onChange: (value: T) => void;
  accessibilityLabel: string;
}

/** Two or three mutually exclusive options, e.g. Liste | Karte or Lieferung | Abholung. */
export function SegmentedControl<T extends string>({
  segments,
  value,
  onChange,
  accessibilityLabel,
}: SegmentedControlProps<T>) {
  const styles = useStyles();
  return (
    <View style={styles.track} accessibilityRole="tablist" accessibilityLabel={forSpeech(accessibilityLabel)}>
      {segments.map((segment) => {
        const selected = segment.value === value;
        return (
          <Pressable
            key={segment.value}
            onPress={() => onChange(segment.value)}
            accessibilityRole="tab"
            accessibilityLabel={forSpeech(segment.label)}
            accessibilityState={{ selected }}
            style={[styles.segment, selected && styles.selected]}>
            {segment.icon !== undefined && (
              <Icon name={segment.icon} size={18} color={selected ? styles.selectedText.color : styles.text.color} />
            )}
            <AppText variant="caption" dense style={selected ? styles.selectedText : styles.text}>
              {segment.label}
            </AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  track: {
    flexDirection: 'row',
    backgroundColor: t.colors.surfaceMuted,
    borderRadius: t.radii.pill,
    padding: 3,
  },
  segment: {
    flex: 1,
    minHeight: 40,
    flexDirection: 'row',
    gap: t.spacing.xs,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: t.radii.pill - 2,
    paddingHorizontal: t.spacing.md,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  // Selected segment: its own fill (white in light, a lighter elevated grey in dark) plus an
  // outline in `controlTrackOff` (≥ 3:1 against the track), so the choice does not rely on a
  // shadow that is invisible on dark backgrounds.
  selected: {
    backgroundColor: t.colors.segmentSelected,
    borderColor: t.colors.controlTrackOff,
    shadowColor: t.colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  text: { color: t.colors.textSecondary },
  selectedText: { color: t.colors.text, fontFamily: t.type.bodyStrong.fontFamily },
}));
