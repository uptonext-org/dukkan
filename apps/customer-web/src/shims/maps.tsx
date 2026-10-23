// react-native-maps for the browser, on Leaflet with the configured raster tiles. Covers what the
// app uses: MapView (initialRegion, onRegionChangeComplete) and Marker (coordinate, onPress,
// custom pin as children).
import L from 'leaflet';
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { View } from 'react-native-web';

import { config } from '../config';

export interface Region {
  latitude: number;
  longitude: number;
  latitudeDelta: number;
  longitudeDelta: number;
}

export interface LatLng {
  latitude: number;
  longitude: number;
}

const MapContext = createContext<L.Map | null>(null);

export function regionToBounds(region: Region): L.LatLngBoundsExpression {
  return [
    [region.latitude - region.latitudeDelta / 2, region.longitude - region.longitudeDelta / 2],
    [region.latitude + region.latitudeDelta / 2, region.longitude + region.longitudeDelta / 2],
  ];
}

export function boundsToRegion(map: L.Map): Region {
  const center = map.getCenter();
  const bounds = map.getBounds();
  return {
    latitude: center.lat,
    longitude: center.lng,
    latitudeDelta: bounds.getNorth() - bounds.getSouth(),
    longitudeDelta: bounds.getEast() - bounds.getWest(),
  };
}

interface MapViewProps {
  style?: unknown;
  initialRegion?: Region;
  region?: Region;
  onRegionChangeComplete?: (region: Region) => void;
  accessibilityLabel?: string;
  testID?: string;
  children?: ReactNode;
  [other: string]: unknown;
}

export default function MapView({ style, initialRegion, region, onRegionChangeComplete, accessibilityLabel, testID, children }: MapViewProps) {
  const element = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<L.Map | null>(null);
  const onChange = useRef(onRegionChangeComplete);
  useEffect(() => {
    onChange.current = onRegionChangeComplete;
  }, [onRegionChangeComplete]);
  const start = useRef(region ?? initialRegion);

  useEffect(() => {
    const host = element.current;
    if (host === null) {
      return undefined;
    }
    const instance = L.map(host, { zoomControl: true, attributionControl: false });
    L.control.attribution({ prefix: '<a href="https://leafletjs.com">Leaflet</a>' }).addTo(instance);
    L.tileLayer(config.mapTileUrl, { attribution: config.mapAttribution, maxZoom: 19 }).addTo(instance);
    if (start.current !== undefined) {
      instance.fitBounds(regionToBounds(start.current));
    } else {
      instance.setView([53.55, 10.0], 12);
    }
    instance.on('moveend', () => onChange.current?.(boundsToRegion(instance)));
    // The map sits in a flex layout that may still be sizing itself on the first frame.
    const resize = new ResizeObserver(() => instance.invalidateSize());
    resize.observe(host);
    setMap(instance);
    return () => {
      resize.disconnect();
      instance.remove();
      setMap(null);
    };
  }, []);

  useEffect(() => {
    if (map !== null && region !== undefined) {
      map.fitBounds(regionToBounds(region));
    }
  }, [map, region]);

  return (
    <View style={[{ flex: 1, overflow: 'hidden' }, style] as never} testID={testID}>
      <div ref={element} role="application" aria-label={accessibilityLabel} style={{ position: 'absolute', inset: 0 }} />
      {map !== null && <MapContext.Provider value={map}>{children}</MapContext.Provider>}
    </View>
  );
}

interface MarkerProps {
  coordinate: LatLng;
  onPress?: () => void;
  accessibilityLabel?: string;
  testID?: string;
  title?: string;
  children?: ReactNode;
  [other: string]: unknown;
}

export function Marker({ coordinate, onPress, accessibilityLabel, testID, title, children }: MarkerProps) {
  const map = useContext(MapContext);
  const [pin, setPin] = useState<HTMLElement | null>(null);
  const press = useRef(onPress);
  useEffect(() => {
    press.current = onPress;
  }, [onPress]);
  const { latitude, longitude } = coordinate;
  const label = accessibilityLabel ?? title;

  useEffect(() => {
    if (map === null) {
      return undefined;
    }
    const icon = L.divIcon({ html: '', className: 'dukkan-pin', iconSize: [0, 0] });
    const marker = L.marker([latitude, longitude], { icon, keyboard: false });
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        press.current?.();
      }
    };
    // Leaflet's icon element becomes a focusable button; the app's pin view renders into it.
    marker.on('add', () => {
      const element = marker.getElement();
      if (element === undefined) {
        return;
      }
      element.setAttribute('role', 'button');
      element.tabIndex = 0;
      if (label !== undefined) {
        element.setAttribute('aria-label', label);
      }
      if (testID !== undefined) {
        element.dataset.testid = testID;
      }
      element.addEventListener('keydown', keydown);
      setPin(element);
    });
    marker.on('click', () => press.current?.());
    marker.addTo(map);
    return () => {
      marker.remove();
      setPin(null);
    };
  }, [map, latitude, longitude, label, testID]);

  return pin !== null && children !== undefined ? createPortal(children, pin) : null;
}

export const Callout = ({ children }: { children?: ReactNode }) => children ?? null;
export const Circle = () => null;
export const Polygon = () => null;
export const Polyline = () => null;
export const PROVIDER_GOOGLE = 'google';
export const PROVIDER_DEFAULT = undefined;
