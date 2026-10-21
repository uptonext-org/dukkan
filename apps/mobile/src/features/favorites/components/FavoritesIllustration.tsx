import Svg, { G, Path } from 'react-native-svg';

import { useTheme } from '../../../shared/theme';

/**
 * Rounded rectangle as a path. (`Rect`'s x/y collide with react-native-svg's deprecated transform
 * shorthands in its typings, so the drawing uses paths only.)
 */
function roundedRect(x: number, y: number, w: number, h: number, r: number): string {
  return `M${x + r} ${y} H${x + w - r} A${r} ${r} 0 0 1 ${x + w} ${y + r} V${y + h - r} A${r} ${r} 0 0 1 ${x + w - r} ${y + h} H${x + r} A${r} ${r} 0 0 1 ${x} ${y + h - r} V${y + r} A${r} ${r} 0 0 1 ${x + r} ${y} Z`;
}

const HEART = 'M0 -4 C -3 -10 -12 -8 -12 -1 C -12 5 -4 9 0 13 C 4 9 12 5 12 -1 C 12 -8 3 -10 0 -4 Z';

/**
 * Our own vector drawing (design-system.md §4.3): three tilted tiles in the tile tints, each with
 * a white heart badge. Decorative, so hidden from screen readers. Not mirrored in RTL
 * (right-to-left.md › Images: don't flip artwork).
 */
export function FavoritesIllustration({ width = 300 }: { width?: number }) {
  const { colors } = useTheme();
  const tiles = [
    { x: 40, y: 50, rotate: -8, fill: colors.tileSage },
    { x: 115, y: 30, rotate: 0, fill: colors.tileSky },
    { x: 190, y: 50, rotate: 8, fill: colors.tileBlush },
  ];
  return (
    <Svg
      width={width}
      height={(width * 190) / 300}
      viewBox="0 0 300 190"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      testID="favorites-illustration">
      {tiles.map((tile) => (
        <G key={tile.x} transform={`rotate(${tile.rotate} ${tile.x + 37} ${tile.y + 60})`}>
          <Path d={roundedRect(tile.x, tile.y + 10, 74, 100, 14)} fill={colors.surface} />
          <Path d={roundedRect(tile.x + 4, tile.y + 14, 66, 92, 12)} fill={tile.fill} />
          <Path d={roundedRect(tile.x + 20, tile.y + 44, 34, 40, 8)} fill={colors.surface} opacity={0.7} />
          <G transform={`translate(${tile.x + 37} ${tile.y + 8})`}>
            <Path d={roundedRect(-16, -16, 32, 32, 16)} fill={colors.surface} />
            <Path d={HEART} fill={colors.accent} transform="scale(0.9)" />
          </G>
        </G>
      ))}
    </Svg>
  );
}
