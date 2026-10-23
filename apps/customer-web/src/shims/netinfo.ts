// @react-native-community/netinfo for the browser: navigator.onLine and its events.
import { useEffect, useState } from 'react';

interface NetState {
  type: 'wifi' | 'none';
  isConnected: boolean;
  isInternetReachable: boolean;
  details: null;
}

const state = (): NetState => ({
  type: navigator.onLine ? 'wifi' : 'none',
  isConnected: navigator.onLine,
  isInternetReachable: navigator.onLine,
  details: null,
});

export function addEventListener(listener: (s: NetState) => void): () => void {
  const handler = () => listener(state());
  window.addEventListener('online', handler);
  window.addEventListener('offline', handler);
  handler();
  return () => {
    window.removeEventListener('online', handler);
    window.removeEventListener('offline', handler);
  };
}

export function useNetInfo(): NetState {
  const [value, setValue] = useState(state);
  useEffect(() => addEventListener(setValue), []);
  return value;
}

const NetInfo = {
  addEventListener,
  useNetInfo,
  fetch: () => Promise.resolve(state()),
  refresh: () => Promise.resolve(state()),
  configure: () => undefined,
};
export default NetInfo;
