import type { DevicePlatform } from '../api';

/**
 * Push token of this device (FCM) behind an interface, like PhotoPicker and SocialSignIn. The app
 * ships `createUnavailablePushRegistration()` until a Firebase project exists; notifications then
 * still reach the in-app inbox. docs/setup.md shows the `@react-native-firebase/messaging` adapter.
 */
export interface PushRegistration {
  /** Asks for permission if needed; null when push is unavailable or declined. */
  deviceToken(): Promise<{ token: string; platform: DevicePlatform } | null>;
}

export function createUnavailablePushRegistration(): PushRegistration {
  return { deviceToken: () => Promise.resolve(null) };
}

export function createFakePushRegistration(token: string | null, platform: DevicePlatform = 'ANDROID'): PushRegistration {
  return { deviceToken: () => Promise.resolve(token === null ? null : { token, platform }) };
}
