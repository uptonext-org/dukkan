import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import type { GeoPoint } from '../api/types';
import { STORAGE_KEYS, type KeyValueStorage } from '../storage';
import type { LocationService } from './LocationService';

/** The place the marketplace is centred on. Stored only on the device. */
export interface ChosenPlace {
  label: string;
  /** Short form for the header button, e.g. the district ("St. Georg"). */
  shortLabel: string;
  location: GeoPoint;
}

interface LocationContextValue {
  status: 'restoring' | 'ready';
  place: ChosenPlace | null;
  setPlace: (place: ChosenPlace) => Promise<void>;
  service: LocationService;
}

const LocationContext = createContext<LocationContextValue | null>(null);

function parsePlace(raw: string | null): ChosenPlace | null {
  if (raw === null) {
    return null;
  }
  try {
    const value = JSON.parse(raw) as Partial<ChosenPlace>;
    if (
      typeof value.label === 'string' &&
      typeof value.location?.lat === 'number' &&
      typeof value.location.lng === 'number'
    ) {
      return {
        label: value.label,
        shortLabel: typeof value.shortLabel === 'string' ? value.shortLabel : value.label,
        location: { lat: value.location.lat, lng: value.location.lng },
      };
    }
  } catch {
    // corrupt value: ask again
  }
  return null;
}

export function LocationProvider({
  storage,
  service,
  children,
}: {
  storage: KeyValueStorage;
  service: LocationService;
  children: ReactNode;
}) {
  const [state, setState] = useState<{ status: 'restoring' | 'ready'; place: ChosenPlace | null }>({
    status: 'restoring',
    place: null,
  });

  useEffect(() => {
    let active = true;
    void storage.get(STORAGE_KEYS.place).then((raw) => {
      if (active) {
        setState({ status: 'ready', place: parsePlace(raw) });
      }
    });
    return () => {
      active = false;
    };
  }, [storage]);

  const setPlace = useCallback(
    async (place: ChosenPlace) => {
      setState({ status: 'ready', place });
      await storage.set(STORAGE_KEYS.place, JSON.stringify(place));
    },
    [storage],
  );

  const value = useMemo(
    () => ({ status: state.status, place: state.place, setPlace, service }),
    [state, setPlace, service],
  );
  return <LocationContext value={value}>{children}</LocationContext>;
}

export function useChosenPlace(): LocationContextValue {
  const value = use(LocationContext);
  if (value === null) {
    throw new Error('useChosenPlace must be used inside <LocationProvider>.');
  }
  return value;
}

/** Place → ChosenPlace with a short header label (district, else street, else city). */
export function toChosenPlace(place: {
  label: string;
  district?: string | null;
  street?: string | null;
  city: string;
  location: GeoPoint;
}): ChosenPlace {
  const short = place.district ?? place.street ?? place.city;
  return { label: place.label, shortLabel: short, location: place.location };
}

/** Hamburg city centre, used as the initial map region before a place is chosen (decision 13). */
export const HAMBURG: GeoPoint = { lat: 53.5511, lng: 9.9937 };
