import { Image, View, type StyleProp, type ViewStyle } from 'react-native';

import { makeStyles, tintColor, useTheme, type TileTint } from '../theme';
import { Icon, type IconName } from './Icon';

interface ImageTileProps {
  uri?: string | null | undefined;
  tint?: TileTint;
  size?: number;
  aspectRatio?: number;
  radius?: number;
  placeholderIcon?: IconName;
  style?: StyleProp<ViewStyle>;
}

/**
 * Photo on a soft tinted tile. Without a photo it shows a uniform placeholder
 * (decision 24). Images are decorative here; the parent row carries the label.
 */
export function ImageTile({ uri, tint = 'sage', size, aspectRatio = 1, radius, placeholderIcon = 'package', style }: ImageTileProps) {
  const styles = useStyles();
  const { colors, radii } = useTheme();
  const r = radius ?? radii.tile;
  return (
    <View
      style={[
        styles.tile,
        { backgroundColor: tintColor(colors, tint), borderRadius: r },
        size !== undefined ? { width: size, height: size } : { width: '100%', aspectRatio },
        style,
      ]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants">
      {uri != null && uri !== '' ? (
        <Image source={{ uri }} style={styles.image} resizeMode="contain" accessibilityIgnoresInvertColors />
      ) : (
        <Icon name={placeholderIcon} size={size !== undefined ? Math.max(16, size * 0.36) : 40} color={colors.textSecondary} strokeWidth={1.5} />
      )}
    </View>
  );
}

const useStyles = makeStyles(() => ({
  tile: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  image: { width: '82%', height: '82%' },
}));
