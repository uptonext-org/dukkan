import * as Keychain from 'react-native-keychain';

import { parseStoredCredentials, type TokenStorage } from './TokenStorage';

const SERVICE = 'app.dukkan.mobile.auth';
const ACCOUNT = 'dukkan';

/** Stores the session tokens (JSON) in the iOS Keychain / Android Keystore-backed storage. */
export function createKeychainTokenStorage(): TokenStorage {
  return {
    async get() {
      try {
        const credentials = await Keychain.getGenericPassword({ service: SERVICE });
        return credentials === false ? null : parseStoredCredentials(credentials.password);
      } catch {
        return null;
      }
    },
    async set(credentials) {
      await Keychain.setGenericPassword(ACCOUNT, JSON.stringify(credentials), {
        service: SERVICE,
        accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
      });
    },
    async clear() {
      await Keychain.resetGenericPassword({ service: SERVICE });
    },
  };
}
