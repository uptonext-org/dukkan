import { getLocales } from 'react-native-localize';

import { createUnavailableSocialSignIn, type SocialSignIn } from '../shared/auth';
import { createUnavailablePushRegistration, type PushRegistration } from '../shared/push';
import { env } from '../shared/config/env';
import { createGeolocationService } from '../shared/location';
import type { PhotoPicker } from '../shared/media';
import { createImagePickerPhotoPicker } from '../shared/media/imagePickerPhotoPicker';
import type { ShoppingInput } from '../shared/shoppingInput';
import { createNativeShoppingInput } from '../shared/shoppingInput/nativeShoppingInput';
import { createAsyncKeyValueStorage, createKeychainTokenStorage, type TokenStorage } from '../shared/storage';
import type { AppDependencies } from './AppProviders';
import { assembleDependencies } from './assembleDependencies';

/** The iOS/Android services; the web app (apps/customer-web) supplies its own. */
export function createDependencies(
  tokenStorage: TokenStorage = createKeychainTokenStorage(),
  photoPicker: PhotoPicker = createImagePickerPhotoPicker(),
  socialSignIn: SocialSignIn = createUnavailableSocialSignIn(),
  pushRegistration: PushRegistration = createUnavailablePushRegistration(),
  shoppingInput: ShoppingInput = createNativeShoppingInput(),
): AppDependencies {
  return assembleDependencies({
    apiBaseUrl: env.apiBaseUrl,
    requestTimeoutMs: env.requestTimeoutMs,
    tokenStorage,
    storage: createAsyncKeyValueStorage(),
    preferredLocales: () => getLocales().map((l) => l.languageTag),
    locationService: createGeolocationService(),
    photoPicker,
    shoppingInput,
    socialSignIn,
    pushRegistration,
  });
}
