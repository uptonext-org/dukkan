/* Jest setup: mock native modules that have no JS implementation in Node. */
import mockSafeAreaContext from 'react-native-safe-area-context/jest/mock';

jest.mock('react-native-safe-area-context', () => mockSafeAreaContext);

jest.mock(
  '@react-native-community/netinfo',
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  (): unknown => require('@react-native-community/netinfo/jest/netinfo-mock.js'),
);

jest.mock('react-native-keychain', () => {
  const store = new Map<string, { username: string; password: string }>();
  return {
    ACCESSIBLE: { WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'AccessibleWhenUnlockedThisDeviceOnly' },
    setGenericPassword: jest.fn((username: string, password: string, options?: { service?: string }) => {
      store.set(options?.service ?? 'default', { username, password });
      return Promise.resolve({ service: options?.service ?? 'default', storage: 'mock' });
    }),
    getGenericPassword: jest.fn((options?: { service?: string }) =>
      Promise.resolve(store.get(options?.service ?? 'default') ?? false),
    ),
    resetGenericPassword: jest.fn((options?: { service?: string }) => {
      store.delete(options?.service ?? 'default');
      return Promise.resolve(true);
    }),
  };
});

jest.mock('@react-native-async-storage/async-storage', () => {
  const data = new Map<string, string>();
  return {
    __esModule: true,
    default: {
      getItem: jest.fn((key: string) => Promise.resolve(data.get(key) ?? null)),
      setItem: jest.fn((key: string, value: string) => {
        data.set(key, value);
        return Promise.resolve();
      }),
      removeItem: jest.fn((key: string) => {
        data.delete(key);
        return Promise.resolve();
      }),
    },
  };
});

jest.mock('react-native-localize', () => ({
  getLocales: jest.fn(() => [{ languageTag: 'de-DE', languageCode: 'de', countryCode: 'DE', isRTL: false }]),
}));

jest.mock('@react-native-community/geolocation', () => ({
  __esModule: true,
  default: { setRNConfiguration: jest.fn(), getCurrentPosition: jest.fn() },
}));

/** react-native-maps renders native views; in Node it becomes plain Views that keep their props. */
jest.mock('react-native-maps', () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { View } = require('react-native') as { View: unknown };
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createElement } = require('react') as { createElement: (type: unknown, props: object) => unknown };
  const MapView = (props: Record<string, unknown>) => createElement(View, props);
  const Marker = (props: Record<string, unknown> & { onPress?: () => void }) =>
    createElement(View, { ...props, accessibilityRole: 'button', onTouchEnd: props.onPress });
  return { __esModule: true, default: MapView, Marker };
});
