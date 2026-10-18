import * as Keychain from 'react-native-keychain';

import type { TokenStorage } from './TokenStorage';

const SERVICE = 'app.dukkan.mobile.auth';
const ACCOUNT = 'dukkan';

/** Stores the JWT in the iOS Keychain / Android Keystore-backed storage. */
export function createKeychainTokenStorage(): TokenStorage {
  return {
    async getToken() {
      try {
        const credentials = await Keychain.getGenericPassword({ service: SERVICE });
        return credentials === false ? null : credentials.password;
      } catch {
        return null;
      }
    },
    async setToken(token) {
      await Keychain.setGenericPassword(ACCOUNT, token, {
        service: SERVICE,
        accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
      });
    },
    async clearToken() {
      await Keychain.resetGenericPassword({ service: SERVICE });
    },
  };
}
