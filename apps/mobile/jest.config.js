/** @type {import('jest').Config} */
module.exports = {
  preset: '@react-native/jest-preset',
  setupFiles: ['<rootDir>/jest.setup.ts'],
  setupFilesAfterEnv: ['<rootDir>/jest.afterEach.ts'],
  // lucide-react-native resolves to .mjs under the "react-native" export condition.
  transform: { '\\.mjs$': 'babel-jest' },
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?|@react-navigation|react-native-screens|react-native-safe-area-context|react-native-keychain|react-native-svg|lucide-react-native)/)',
  ],
  // Full-app integration tests render the real navigators; give slow CI machines headroom.
  testTimeout: 20000,
  testPathIgnorePatterns: ['/node_modules/', '/android/', '/ios/'],
  collectCoverageFrom: ['src/**/*.{ts,tsx}', '!src/**/__tests__/**', '!src/test/**'],
};
