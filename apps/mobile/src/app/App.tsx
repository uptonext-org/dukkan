import { DefaultTheme, NavigationContainer, type Theme } from '@react-navigation/native';
import { useState } from 'react';
import { StatusBar, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { RootNavigator } from '../navigation/RootNavigator';
import { connectOnlineManager } from '../shared/network/onlineManager';
import { colors } from '../shared/theme';
import { OfflineBanner } from '../shared/ui';
import { AppProviders } from './AppProviders';
import { createDependencies } from './createDependencies';

connectOnlineManager();

const navigationTheme: Theme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: colors.primary,
    background: colors.background,
    card: colors.surface,
    text: colors.text,
    border: colors.border,
    notification: colors.promo,
  },
};

export default function App() {
  const [dependencies] = useState(createDependencies);
  return (
    <AppProviders dependencies={dependencies}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.root}>
        <SafeAreaView edges={['top']} style={styles.banner}>
          <OfflineBanner />
        </SafeAreaView>
        <NavigationContainer theme={navigationTheme}>
          <RootNavigator />
        </NavigationContainer>
      </View>
    </AppProviders>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.background },
  banner: { backgroundColor: colors.background },
});
