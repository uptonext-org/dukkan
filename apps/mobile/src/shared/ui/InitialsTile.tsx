import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { colors, radii } from '../theme';

export function initialsOf(name: string): string {
  const words = name
    .trim()
    .split(/\s+/)
    .filter((w) => w.length > 0);
  const letters = words.slice(0, 2).map((w) => w.charAt(0).toUpperCase());
  return letters.join('') || '?';
}

function colorFor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) {
    hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  }
  return colors.tileColors[hash % colors.tileColors.length] ?? colors.surfaceAlt;
}

/** Placeholder product image: the seed data has no images (imageUrl is null). */
export const InitialsTile = memo(function InitialsTile({ name, size }: { name: string; size: number }) {
  return (
    <View
      style={[styles.tile, { width: size, height: size, backgroundColor: colorFor(name) }]}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants">
      <Text style={[styles.text, { fontSize: size * 0.32 }]} allowFontScaling={false}>
        {initialsOf(name)}
      </Text>
    </View>
  );
});

const styles = StyleSheet.create({
  tile: { borderRadius: radii.md, alignItems: 'center', justifyContent: 'center' },
  text: { fontWeight: '700', color: colors.text },
});
