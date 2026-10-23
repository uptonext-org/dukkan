import { useState } from 'react';
import { View } from 'react-native';

import { AppProviders } from '../../mobile/src/app/AppProviders';
import { AppRoot, useRestoredLanguage } from '../../mobile/src/app/AppRoot';
import { buildLinking } from '../../mobile/src/navigation/linking';
import { connectOnlineManager } from '../../mobile/src/shared/network/onlineManager';
import { config } from './config';
import { createWebDependencies } from './dependencies';

connectOnlineManager();

/** Screens follow the address bar: /markets/<id>/products/<id>, /orders/<id>, /reset-password?token=… */
const linking = buildLinking([window.location.origin]);

/** The customer app in the browser; on wide screens it keeps a phone-width column (index.html). */
export function WebApp() {
  const [dependencies] = useState(() => createWebDependencies(config));
  const languageReady = useRestoredLanguage(dependencies.languageStore);
  if (!languageReady) {
    return <View style={{ flex: 1 }} />;
  }
  return (
    <AppProviders dependencies={dependencies}>
      <AppRoot linking={linking} />
    </AppProviders>
  );
}
