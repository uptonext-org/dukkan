// Web stand-in for @react-native-community/geolocation. Uses navigator.geolocation when the
// browser grants it (Playwright can set a fixed position), otherwise reports "unavailable".
const Geolocation = {
  setRNConfiguration() {},
  requestAuthorization(success) {
    success?.();
  },
  getCurrentPosition(success, error, options) {
    if (!('geolocation' in navigator)) {
      error?.({ code: 2, message: 'unavailable' });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (p) => success({ coords: { latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy }, timestamp: p.timestamp }),
      (e) => error?.({ code: e.code, message: e.message }),
      options,
    );
  },
  watchPosition() {
    return 0;
  },
  clearWatch() {},
  stopObserving() {},
};
export default Geolocation;
