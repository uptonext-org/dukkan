import { Switch, type SwitchProps } from 'react-native';

import { forSpeech } from '../format/bidi';
import { useTheme } from '../theme';

type ToggleSwitchProps = Omit<SwitchProps, 'trackColor' | 'ios_backgroundColor' | 'thumbColor'> & { accessibilityLabel: string };

/**
 * Switch with token colours: on = `primary`, off = `controlTrackOff`, which keeps ≥ 3:1 non-text
 * contrast against the surface in light and dark mode (accessibility.md, design-system.md §1).
 * The platform default off-track (or `surfaceMuted`) was about 1.1:1 and nearly invisible.
 */
export function ToggleSwitch({ accessibilityLabel, ...props }: ToggleSwitchProps) {
  const { colors } = useTheme();
  return (
    <Switch
      {...props}
      accessibilityLabel={forSpeech(accessibilityLabel)}
      trackColor={{ true: colors.primary, false: colors.controlTrackOff }}
      ios_backgroundColor={colors.controlTrackOff}
      thumbColor={colors.overlayText}
    />
  );
}
