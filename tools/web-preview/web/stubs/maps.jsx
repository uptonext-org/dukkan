// Web stand-in for react-native-maps: NOT a map. A labelled placeholder that lists the markers,
// so map mode can still be exercised and screenshotted. Markers stay pressable.
import { Children, isValidElement } from 'react';
import { Pressable, Text, View } from 'react-native';

const styles = {
  root: { flex: 1, backgroundColor: '#DDE6D5', overflow: 'hidden' },
  label: {
    alignSelf: 'flex-start',
    margin: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    backgroundColor: 'rgba(0,0,0,0.65)',
  },
  labelText: { color: '#fff', fontSize: 12, fontFamily: 'Inter-SemiBold' },
  coords: { color: 'rgba(255,255,255,0.85)', fontSize: 11, fontFamily: 'Inter-Regular' },
  list: { paddingHorizontal: 8, gap: 6 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
};

export default function MapView({ style, children, initialRegion, region, testID, accessibilityLabel }) {
  const r = region ?? initialRegion;
  return (
    <View style={[styles.root, style]} testID={testID} accessibilityLabel={accessibilityLabel}>
      <View style={styles.label}>
        <Text style={styles.labelText}>Map preview (web stand-in, not a real map)</Text>
        {r ? (
          <Text style={styles.coords}>
            centre {r.latitude.toFixed(4)}, {r.longitude.toFixed(4)}
          </Text>
        ) : null}
      </View>
      <View style={styles.list}>{Children.toArray(children).filter(isValidElement)}</View>
    </View>
  );
}

export function Marker({ children, onPress, accessibilityLabel, testID, coordinate, title }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={accessibilityLabel} testID={testID} style={styles.row}>
      {children}
      <Text style={{ fontFamily: 'Inter-Medium', fontSize: 13, color: '#2B2B2B' }}>
        {accessibilityLabel ?? title ?? ''}{' '}
        <Text style={{ color: '#5C5752', fontSize: 11 }}>
          ({coordinate.latitude.toFixed(4)}, {coordinate.longitude.toFixed(4)})
        </Text>
      </Text>
    </Pressable>
  );
}
export const Callout = ({ children }) => children ?? null;
export const Circle = () => null;
export const Polygon = () => null;
export const Polyline = () => null;
export const PROVIDER_GOOGLE = 'google';
export const PROVIDER_DEFAULT = undefined;
