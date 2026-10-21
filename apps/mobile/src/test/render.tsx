import { NavigationContainer } from '@react-navigation/native';

import { createUnavailableSocialSignIn, type SocialSignIn } from '../shared/auth';
import { createUnavailablePushRegistration, type PushRegistration } from '../shared/push';
import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';

import { AppProviders, type AppDependencies } from '../app/AppProviders';
import { AppRoot } from '../app/AppRoot';
import { createAuthSession } from '../features/auth/session';
import type { ApiClient, GeoPoint } from '../shared/api';
import { forSpeech } from '../shared/format/bidi';
import { createI18n, createLanguageStore, type Language } from '../shared/i18n';
import type { ChosenPlace, LocationService } from '../shared/location';
import { createFakePhotoPicker, type PhotoPicker } from '../shared/media';
import { createMemoryKeyValueStorage, createMemoryTokenStorage, STORAGE_KEYS } from '../shared/storage';

export const ST_GEORG: ChosenPlace = { label: 'Lange Reihe 12, 20099 Hamburg (St. Georg)', shortLabel: 'St. Georg', location: { lat: 53.5552, lng: 10.0105 } };

export interface TestOptions {
  token?: string | null;
  language?: Language;
  place?: ChosenPlace | null;
  location?: LocationService;
  photoPicker?: PhotoPicker;
  socialSignIn?: SocialSignIn;
  pushRegistration?: PushRegistration;
}

const clients = new Set<QueryClient>();

/** Stops React Query timers (refetchInterval, retries) between tests so Jest workers exit cleanly. */
export function disposeQueryClients(): void {
  for (const client of clients) {
    client.cancelQueries().catch(() => undefined);
    client.clear();
    client.unmount();
  }
  clients.clear();
}

export function createTestDependencies(apiClient: ApiClient, options: TestOptions = {}): AppDependencies {
  const language = options.language ?? 'de';
  const place = options.place === undefined ? ST_GEORG : options.place;
  const storage = createMemoryKeyValueStorage(place !== null ? { [STORAGE_KEYS.place]: JSON.stringify(place) } : {});
  const languageStore = createLanguageStore(storage, () => [`${language}-DE`], language);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity, staleTime: Infinity }, mutations: { retry: false, gcTime: Infinity } },
  });
  clients.add(queryClient);
  return {
    apiClient,
    queryClient,
    session: createAuthSession(createMemoryTokenStorage(options.token ?? null)),
    languageStore,
    i18n: createI18n(language),
    storage,
    locationService: options.location ?? { getCurrentPosition: () => Promise.resolve<GeoPoint>(ST_GEORG.location) },
    photoPicker: options.photoPicker ?? createFakePhotoPicker({ status: 'cancelled' }),
    socialSignIn: options.socialSignIn ?? createUnavailableSocialSignIn(),
    pushRegistration: options.pushRegistration ?? createUnavailablePushRegistration(),
  };
}

/** Any element inside the app providers, wrapped in a NavigationContainer (no navigator). */
export function renderWithProviders(ui: ReactElement, dependencies: AppDependencies, scheme: 'light' | 'dark' = 'light') {
  return render(
    <AppProviders dependencies={dependencies} scheme={scheme}>
      <NavigationContainer>{ui}</NavigationContainer>
    </AppProviders>,
  );
}

/** The real app tree (root navigator) with the given dependencies. */
export function renderApp(dependencies: AppDependencies) {
  return render(
    <AppProviders dependencies={dependencies} scheme="light">
      <AppRoot />
    </AppProviders>,
  );
}

/**
 * de-DE uses NBSP before the euro sign; tests compare with normal spaces. Interpolated numbers
 * carry invisible bidi isolates (shared/format/bidi.ts), which tests also ignore here.
 */
export const normalize = (s: string) =>
  forSpeech(s).split(String.fromCharCode(160)).join(' ').split(String.fromCharCode(8239)).join(' ');

/** Renders `ui` inside the real app root (providers + root `direction` view), without the navigator. */
export function renderInAppRoot(ui: ReactElement, dependencies: AppDependencies, scheme: 'light' | 'dark' = 'light') {
  return render(
    <AppProviders dependencies={dependencies} scheme={scheme}>
      <AppRoot>{ui}</AppRoot>
    </AppProviders>,
  );
}

interface HostLike {
  props: { style?: unknown };
  parent: HostLike | null;
}

function flat(style: unknown): Record<string, unknown> {
  if (Array.isArray(style)) {
    return Object.assign({}, ...style.map(flat)) as Record<string, unknown>;
  }
  return typeof style === 'object' && style !== null ? (style as Record<string, unknown>) : {};
}

/** Layout direction a host element inherits (nearest ancestor `direction` style). */
export function inheritedDirection(element: HostLike): string | undefined {
  let node: HostLike | null = element;
  while (node !== null) {
    const direction = flat(node.props.style).direction;
    if (typeof direction === 'string') {
      return direction;
    }
    node = node.parent;
  }
  return undefined;
}

export function flatStyle(element: { props: { style?: unknown } }): Record<string, unknown> {
  return flat(element.props.style);
}
