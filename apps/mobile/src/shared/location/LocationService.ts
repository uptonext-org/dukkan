import Geolocation from '@react-native-community/geolocation';

import type { GeoPoint } from '../api/types';

export type LocationFailure = 'denied' | 'unavailable' | 'timeout';

export class LocationError extends Error {
  readonly reason: LocationFailure;
  constructor(reason: LocationFailure) {
    super(`Location ${reason}`);
    this.name = 'LocationError';
    this.reason = reason;
  }
}

/** Device position behind an interface so screens can be tested without native modules. */
export interface LocationService {
  /**
   * Asks for "when in use" permission if needed (iOS offers "Allow once" / "While using the app";
   * Android coarse/fine foreground) and resolves the current position. Never requests "always".
   */
  getCurrentPosition(): Promise<GeoPoint>;
}

const PERMISSION_DENIED = 1;
const TIMEOUT = 3;

export function createGeolocationService(): LocationService {
  Geolocation.setRNConfiguration({
    skipPermissionRequests: false,
    authorizationLevel: 'whenInUse',
    locationProvider: 'auto',
    enableBackgroundLocationUpdates: false,
  });
  return {
    getCurrentPosition() {
      return new Promise<GeoPoint>((resolve, reject) => {
        Geolocation.getCurrentPosition(
          (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
          (error) => {
            const reason: LocationFailure =
              error.code === PERMISSION_DENIED ? 'denied' : error.code === TIMEOUT ? 'timeout' : 'unavailable';
            reject(new LocationError(reason));
          },
          // Coarse is enough for "markets near me"; a cached fix up to 5 minutes old is fine.
          { enableHighAccuracy: false, timeout: 15_000, maximumAge: 300_000 },
        );
      });
    },
  };
}
