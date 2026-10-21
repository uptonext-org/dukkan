// Web stand-in for @react-native-community/netinfo using navigator.onLine.
import { useEffect, useState } from 'react';

const state = () => ({
  type: navigator.onLine ? 'wifi' : 'none',
  isConnected: navigator.onLine,
  isInternetReachable: navigator.onLine,
  details: null,
});
function addEventListener(listener) {
  const handler = () => listener(state());
  window.addEventListener('online', handler);
  window.addEventListener('offline', handler);
  handler();
  return () => {
    window.removeEventListener('online', handler);
    window.removeEventListener('offline', handler);
  };
}
export function useNetInfo() {
  const [value, setValue] = useState(state);
  useEffect(() => addEventListener(setValue), []);
  return value;
}
const NetInfo = { addEventListener, fetch: async () => state(), refresh: async () => state(), configure() {}, useNetInfo };
export default NetInfo;
export { addEventListener };
