// Native-only modules the web build never calls: the web app injects browser adapters instead
// (src/platform). They are still imported by shared files, so they resolve to these stand-ins.
const unavailable = (name: string) => () => Promise.reject(new Error(`${name} is not available on the web`));

// react-native-keychain
export const ACCESSIBLE = { WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'WHEN_UNLOCKED_THIS_DEVICE_ONLY' };
export const getGenericPassword = unavailable('keychain');
export const setGenericPassword = unavailable('keychain');
export const resetGenericPassword = unavailable('keychain');

// react-native-image-picker
export const launchCamera = unavailable('image picker');
export const launchImageLibrary = unavailable('image picker');

// dukkan-capture
export const DukkanCapture = new Proxy({}, { get: (_target, name) => unavailable(`DukkanCapture.${String(name)}`) });

// @react-native-community/geolocation and @react-native-async-storage/async-storage
const geolocation = {
  setRNConfiguration: () => undefined,
  getCurrentPosition: (_ok: unknown, fail: (error: { code: number }) => void) => fail({ code: 2 }),
};
const asyncStorage = {
  getItem: () => Promise.resolve(null),
  setItem: () => Promise.resolve(),
  removeItem: () => Promise.resolve(),
};
export default { ...geolocation, ...asyncStorage };
