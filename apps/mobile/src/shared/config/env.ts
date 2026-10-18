import { Platform } from 'react-native';

import appJson from '../../../app.json';

/**
 * API base URL resolution:
 * 1. `apiBaseUrl` in app.json, when set (e.g. a LAN IP for a physical device).
 * 2. Otherwise the local backend: Android emulators reach the host machine via 10.0.2.2,
 *    the iOS simulator via localhost.
 */
export function resolveApiBaseUrl(os: string, override: string | null | undefined): string {
  if (override !== undefined && override !== null && override.trim() !== '') {
    return override.trim().replace(/\/+$/, '');
  }
  const host = os === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
  return `${host}/api/v1`;
}

interface AppJson {
  name: string;
  displayName: string;
  apiBaseUrl?: string | null;
}

export const env = {
  apiBaseUrl: resolveApiBaseUrl(Platform.OS, (appJson as AppJson).apiBaseUrl),
  requestTimeoutMs: 15_000,
} as const;
