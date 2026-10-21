import { getLocales } from 'react-native-localize';

import { createAuthSession } from '../features/auth/session';
import { createUnavailableSocialSignIn, type SocialSignIn } from '../shared/auth';
import { createDukkanApi, createFetchApiClient, createQueryClient } from '../shared/api';
import { env } from '../shared/config/env';
import { createI18n, createLanguageStore } from '../shared/i18n';
import { createGeolocationService } from '../shared/location';
import type { PhotoPicker } from '../shared/media';
import { createImagePickerPhotoPicker } from '../shared/media/imagePickerPhotoPicker';
import { createAsyncKeyValueStorage, createKeychainTokenStorage, type TokenStorage } from '../shared/storage';
import type { AppDependencies } from './AppProviders';

export function createDependencies(
  tokenStorage: TokenStorage = createKeychainTokenStorage(),
  photoPicker: PhotoPicker = createImagePickerPhotoPicker(),
  socialSignIn: SocialSignIn = createUnavailableSocialSignIn(),
): AppDependencies {
  const storage = createAsyncKeyValueStorage();
  const languageStore = createLanguageStore(storage, () => getLocales().map((l) => l.languageTag));
  const session = createAuthSession(tokenStorage);
  const apiClient = createFetchApiClient({
    baseUrl: env.apiBaseUrl,
    timeoutMs: env.requestTimeoutMs,
    getToken: () => session.getToken(),
    getLanguage: () => languageStore.get(),
    refreshSession: () => session.refresh(),
    onUnauthorized: () => session.expire(),
  });
  session.setRefresher((refreshToken) => createDukkanApi(apiClient).refreshSession(refreshToken));
  return {
    apiClient,
    queryClient: createQueryClient(),
    session,
    languageStore,
    i18n: createI18n(languageStore.get()),
    storage,
    locationService: createGeolocationService(),
    photoPicker,
    socialSignIn,
  };
}
