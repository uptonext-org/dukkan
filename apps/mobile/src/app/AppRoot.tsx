import { DarkTheme, DefaultTheme, NavigationContainer, type LinkingOptions, type Theme as NavTheme } from '@react-navigation/native';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { StatusBar, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { RootNavigator } from '../navigation/RootNavigator';
import type { RootStackParamList } from '../navigation/types';
import { useLanguage, type LanguageStore } from '../shared/i18n';
import { useTheme } from '../shared/theme';
import { OfflineBanner } from '../shared/ui';

/**
 * Deep links (scheme in AndroidManifest and Info.plist): the password reset mail opens
 * dukkan://reset-password?token=…, the welcome mail dukkan://verify-email?token=….
 */
const LINKING: LinkingOptions<RootStackParamList> = {
  prefixes: ['dukkan://'],
  config: { screens: { ResetPassword: 'reset-password', VerifyEmail: 'verify-email' } },
};

/**
 * Root view. `direction` mirrors the whole JS UI instantly when Arabic is chosen: Fabric resolves
 * start/end, natural text alignment and horizontal ScrollView order from the Yoga direction.
 */
export function AppRoot({ children }: { children?: ReactNode }) {
  const { direction } = useLanguage();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const navigationTheme = useMemo<NavTheme>(() => {
    const base = theme.dark ? DarkTheme : DefaultTheme;
    return {
      ...base,
      colors: {
        ...base.colors,
        primary: theme.colors.accent,
        background: theme.colors.bg,
        card: theme.colors.surface,
        text: theme.colors.text,
        border: theme.colors.separator,
        notification: theme.colors.accentFill,
      },
    };
  }, [theme]);
  return (
    <View style={{ flex: 1, direction, backgroundColor: theme.colors.bg }}>
      <StatusBar barStyle={theme.dark ? 'light-content' : 'dark-content'} />
      <NavigationContainer theme={navigationTheme} linking={LINKING}>
        {children ?? <RootNavigator />}
      </NavigationContainer>
      {/* Floats over the header area so screens keep their own safe-area layout. */}
      <View style={{ position: 'absolute', top: insets.top, start: 0, end: 0 }} pointerEvents="box-none">
        <OfflineBanner />
      </View>
    </View>
  );
}

/** Restores the persisted language before the first frame of UI. */
export function useRestoredLanguage(store: LanguageStore): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    void store
      .restore()
      .catch(() => undefined)
      .finally(() => {
        if (active) {
          setReady(true);
        }
      });
    return () => {
      active = false;
    };
  }, [store]);
  return ready;
}
