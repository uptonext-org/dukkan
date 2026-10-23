/**
 * Runtime settings, read from /config.js (written when the container starts, see
 * deploy/40-dukkan-config.sh), so one image serves staging and production.
 */
export interface WebConfig {
  /** The API on the same origin by default: the proxy routes /api to the backend. */
  apiBaseUrl: string;
  /** Raster tiles for the map, `{z}/{x}/{y}` placeholders (Leaflet syntax). */
  mapTileUrl: string;
  /** Required credit line of the tile provider (HTML allowed, shown on the map). */
  mapAttribution: string;
}

declare global {
  interface Window {
    DUKKAN_CONFIG?: Partial<WebConfig>;
  }
}

const OSM_TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

function nonEmpty(value: string | undefined): string | undefined {
  return value !== undefined && value.trim() !== '' ? value.trim() : undefined;
}

export function readConfig(source: Partial<WebConfig> | undefined, origin: string): WebConfig {
  return {
    apiBaseUrl: (nonEmpty(source?.apiBaseUrl) ?? `${origin}/api/v1`).replace(/\/+$/, ''),
    mapTileUrl: nonEmpty(source?.mapTileUrl) ?? OSM_TILES,
    mapAttribution: nonEmpty(source?.mapAttribution) ?? OSM_ATTRIBUTION,
  };
}

export const config: WebConfig = readConfig(window.DUKKAN_CONFIG, window.location.origin);
