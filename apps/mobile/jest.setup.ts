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
