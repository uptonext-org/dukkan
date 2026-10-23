import { assembleDependencies } from '../../mobile/src/app/assembleDependencies';
import type { AppDependencies } from '../../mobile/src/app/AppProviders';
import { createUnavailableSocialSignIn } from '../../mobile/src/shared/auth/SocialSignIn';
import { createUnavailablePushRegistration } from '../../mobile/src/shared/push/PushRegistration';
import type { WebConfig } from './config';
import { createBrowserLocationService } from './platform/location';
import { createBrowserPhotoPicker } from './platform/photoPicker';
import { createBrowserSpeechInput } from './platform/speech';
import { createLocalKeyValueStorage, createLocalTokenStorage } from './platform/storage';
import { createBrowserVideoFramePicker } from './platform/videoFrames';

/** The app's dependencies with browser services. No push: notifications stay in the in-app inbox. */
export function createWebDependencies(config: WebConfig): AppDependencies {
  return assembleDependencies({
    apiBaseUrl: config.apiBaseUrl,
    requestTimeoutMs: 15_000,
    tokenStorage: createLocalTokenStorage(),
    storage: createLocalKeyValueStorage(),
    preferredLocales: () => [...navigator.languages],
    locationService: createBrowserLocationService(),
    photoPicker: createBrowserPhotoPicker(),
    shoppingInput: { speech: createBrowserSpeechInput(), video: createBrowserVideoFramePicker() },
    socialSignIn: createUnavailableSocialSignIn(),
    pushRegistration: createUnavailablePushRegistration(),
  });
}
