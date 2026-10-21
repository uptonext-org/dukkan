import { useState } from 'react';
import { View } from 'react-native';

import { connectOnlineManager } from '../shared/network/onlineManager';
import { AppProviders } from './AppProviders';
import { AppRoot, useRestoredLanguage } from './AppRoot';
import { createDependencies } from './createDependencies';

connectOnlineManager();

export default function App() {
  const [dependencies] = useState(createDependencies);
  const languageReady = useRestoredLanguage(dependencies.languageStore);
  if (!languageReady) {
    return <View style={{ flex: 1 }} />;
  }
  return (
    <AppProviders dependencies={dependencies}>
      <AppRoot />
    </AppProviders>
  );
}
