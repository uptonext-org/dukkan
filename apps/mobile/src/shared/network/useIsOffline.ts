import { useNetInfo } from '@react-native-community/netinfo';

/** True only when NetInfo positively reports no connection (unknown counts as online). */
export function useIsOffline(): boolean {
  const { isConnected } = useNetInfo();
  return isConnected === false;
}
