import { useState } from 'react';
import { Image, PixelRatio, View, type LayoutChangeEvent, type StyleProp, type ViewStyle } from 'react-native';

import { makeStyles, tintColor, useTheme, type TileTint } from '../theme';
import { Icon, type IconName } from './Icon';
import { sizedImageUrl } from './sizedImageUrl';

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
  // The photo fills 82 % of the tile; load a copy that fits it at the screen's pixel density.
  const [measured, setMeasured] = useState<number | null>(null);
  const width = size ?? measured;
  const onLayout = size === undefined ? (e: LayoutChangeEvent) => setMeasured(e.nativeEvent.layout.width) : undefined;
  const source = uri != null && uri !== '' && width !== null ? sizedImageUrl(uri, Math.ceil(width * 0.82 * PixelRatio.get())) : null;
  return (
    <View
      style={[
        styles.tile,
        { backgroundColor: tintColor(colors, tint), borderRadius: r },
        size !== undefined ? { width: size, height: size } : { width: '100%', aspectRatio },
        style,
      ]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      {...(onLayout !== undefined ? { onLayout } : {})}>
      {uri != null && uri !== '' ? (
        source !== null && <Image source={{ uri: source }} style={styles.image} resizeMode="contain" accessibilityIgnoresInvertColors />
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
