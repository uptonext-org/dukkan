import { useMemo, useState } from 'react';
import { View } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import { useTranslation } from 'react-i18next';

import { forSpeech } from '../../../shared/format';
import type { GeoPoint, MarketSummary } from '../../../shared/api';
import { makeStyles } from '../../../shared/theme';
import { AppText } from '../../../shared/ui';
import { marketInitial } from '../model';
import { MarketCard } from './MarketCard';

interface MarketMapProps {
  center: GeoPoint;
  markets: MarketSummary[];
  onOpen: (market: MarketSummary) => void;
}

/**
 * Map mode: one pin per market (its initial) and a bottom card for the selected market.
 * Apple Maps on iOS, Google Maps on Android (needs DUKKAN_MAPS_API_KEY, see README).
 * The card sits above the map's legal link (maps.md › keep the logo and link visible).
 */
export function MarketMap({ center, markets, onOpen }: MarketMapProps) {
  const styles = useStyles();
  const { t } = useTranslation();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = useMemo(() => markets.find((m) => m.id === selectedId) ?? null, [markets, selectedId]);
  return (
    <View style={styles.root}>
      <MapView
        style={styles.map}
        initialRegion={{ latitude: center.lat, longitude: center.lng, latitudeDelta: 0.08, longitudeDelta: 0.08 }}
        showsUserLocation={false}
        accessibilityLabel={forSpeech(t('discover.mapLabel', { count: markets.length }))}
        testID="market-map"
        mapPadding={{ top: 0, left: 0, right: 0, bottom: selected !== null ? 200 : 0 }}>
        {markets.map((market) => (
          <Marker
            key={market.id}
            coordinate={{ latitude: market.location.lat, longitude: market.location.lng }}
            onPress={() => setSelectedId(market.id)}
            accessibilityLabel={forSpeech(market.name)}
            testID={`marker-${market.id}`}
            tracksViewChanges={false}>
            <View style={[styles.pin, market.id === selectedId && styles.pinSelected]}>
              <AppText variant="caption" dense style={styles.pinText}>
                {marketInitial(market)}
              </AppText>
            </View>
          </Marker>
        ))}
      </MapView>
      {selected !== null && (
        <View style={styles.card} testID="selected-market-card">
          <MarketCard market={selected} onPress={onOpen} compact />
        </View>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1 },
  map: { flex: 1 },
  pin: {
    minWidth: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: t.colors.primaryStrong,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: t.colors.surface,
  },
  pinSelected: { backgroundColor: t.colors.accentFill, transform: [{ scale: 1.15 }] },
  pinText: { color: t.colors.onAccent },
  card: {
    position: 'absolute',
    start: t.layout.gutter,
    end: t.layout.gutter,
    bottom: t.spacing.xxl,
    backgroundColor: t.colors.surface,
    borderRadius: t.radii.tile,
    padding: t.spacing.md,
    shadowColor: t.colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
    elevation: 4,
  },
}));
