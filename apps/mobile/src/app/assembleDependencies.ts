import { createAuthSession } from '../features/auth/session';
import type { SocialSignIn } from '../shared/auth';
import { createDukkanApi, createFetchApiClient, createQueryClient } from '../shared/api';
import { createI18n, createLanguageStore } from '../shared/i18n';
import type { LocationService } from '../shared/location';
import type { PhotoPicker } from '../shared/media';
import type { PushRegistration } from '../shared/push';
import type { ShoppingInput } from '../shared/shoppingInput';
import type { KeyValueStorage, TokenStorage } from '../shared/storage';
import type { AppDependencies } from './AppProviders';

/** What a platform (iOS/Android, the web) supplies; everything else is shared. */
export interface PlatformServices {
  apiBaseUrl: string;
  requestTimeoutMs: number;
  tokenStorage: TokenStorage;
  storage: KeyValueStorage;
  /** The user's preferred locales, most preferred first (BCP 47 tags). */
  preferredLocales: () => string[];
  locationService: LocationService;
  photoPicker: PhotoPicker;
  shoppingInput: ShoppingInput;
  socialSignIn: SocialSignIn;
  pushRegistration: PushRegistration;
}

/** Wires the session, API client and i18n on top of a platform's services. */
export function assembleDependencies(platform: PlatformServices): AppDependencies {
  const { storage } = platform;
  const languageStore = createLanguageStore(storage, platform.preferredLocales);
  const session = createAuthSession(platform.tokenStorage);
  const apiClient = createFetchApiClient({
    baseUrl: platform.apiBaseUrl,
    timeoutMs: platform.requestTimeoutMs,
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
    locationService: platform.locationService,
    photoPicker: platform.photoPicker,
    shoppingInput: platform.shoppingInput,
    socialSignIn: platform.socialSignIn,
    pushRegistration: platform.pushRegistration,
  };
}
