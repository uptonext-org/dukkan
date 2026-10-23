import type { GeoPoint } from '../../../mobile/src/shared/api/types';
import { LocationError, type LocationService } from '../../../mobile/src/shared/location/LocationService';

/** The browser's position (asks for permission on first use; needs https). */
export function createBrowserLocationService(
  geolocation: Geolocation | null = 'geolocation' in navigator ? navigator.geolocation : null,
): LocationService {
  return {
    getCurrentPosition() {
      return new Promise<GeoPoint>((resolve, reject) => {
        if (geolocation === null) {
          reject(new LocationError('unavailable'));
          return;
        }
        geolocation.getCurrentPosition(
          (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
          (error) =>
            reject(
              new LocationError(
                error.code === error.PERMISSION_DENIED ? 'denied' : error.code === error.TIMEOUT ? 'timeout' : 'unavailable',
              ),
            ),
          { enableHighAccuracy: false, timeout: 15_000, maximumAge: 300_000 },
        );
      });
    },
  };
}
