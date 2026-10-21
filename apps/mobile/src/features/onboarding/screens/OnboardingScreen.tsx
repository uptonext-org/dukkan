import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { isApiError, useApi, type GeoPoint, type Place } from '../../../shared/api';
import { formatDistance } from '../../../shared/format';
import { LocationError, PlaceSearch, toChosenPlace, useChosenPlace, type ChosenPlace } from '../../../shared/location';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, IconButton, Screen } from '../../../shared/ui';
import { ShellHeader } from '../../marketplace/components/ShellHeader';

type LocateState = 'idle' | 'locating' | 'denied' | 'failed';

/**
 * "Wo wohnst du?" — explains why before any system prompt (privacy.md › Requesting permission),
 * then offers three equal ways: device location (when in use only), address search, or a pin on
 * the map. A denied permission leaves the screen fully usable through search and map.
 */
export function OnboardingScreen({ navigation, route }: RootStackScreenProps<'Onboarding' | 'Welcome'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const api = useApi();
  const { setPlace, service } = useChosenPlace();
  const changing = route.name === 'Onboarding';
  const [state, setState] = useState<LocateState>('idle');

  const finish = async (place: ChosenPlace) => {
    await setPlace(place);
    if (changing && navigation.canGoBack()) {
      navigation.goBack();
    }
  };

  const placeFromPoint = async (point: GeoPoint): Promise<ChosenPlace> => {
    try {
      return toChosenPlace(await api.reverseGeocode(point.lat, point.lng));
    } catch (error) {
      if (isApiError(error) && error.isOffline) {
        throw error;
      }
      return { label: t('onboarding.currentLocation'), shortLabel: t('onboarding.currentLocation'), location: point };
    }
  };

  const locateDevice = async () => {
    setState('locating');
    try {
      const point = await service.getCurrentPosition();
      await finish(await placeFromPoint(point));
      setState('idle');
    } catch (error) {
      setState(error instanceof LocationError && error.reason === 'denied' ? 'denied' : 'failed');
    }
  };

  return (
    <Screen
      scroll
      header={
        changing ? (
          <View style={styles.changeHeader}>
            <IconButton icon="close" label={t('common.close')} onPress={() => navigation.goBack()} />
          </View>
        ) : (
          <ShellHeader title="" showAccount={false} />
        )
      }>
      <AppText variant="largeTitle" accessibilityRole="header">
        {t('onboarding.title')}
      </AppText>
      <AppText variant="body" color="textSecondary" style={styles.reason}>
        {t('onboarding.reason')}
      </AppText>

      <Button
        title={t('onboarding.useLocation')}
        icon="locate"
        onPress={() => void locateDevice()}
        loading={state === 'locating'}
        accessibilityHint={t('onboarding.useLocationHint')}
        testID="use-location"
      />
      {(state === 'denied' || state === 'failed') && (
        <View style={styles.notice} accessibilityRole="alert" accessibilityLiveRegion="polite" testID="location-denied">
          <Icon name="alert" size={20} color={colors.accent} />
          <AppText variant="subhead" style={styles.flex}>
            {state === 'denied' ? t('onboarding.denied') : t('onboarding.failed')}
          </AppText>
        </View>
      )}

      <AppText variant="subhead" color="textSecondary" style={styles.or}>
        {t('onboarding.orSearch')}
      </AppText>
      <PlaceSearch onSelect={(place: Place) => void finish(toChosenPlace(place))} autoFocus={state === 'denied'} />

      <Button
        title={t('onboarding.pickOnMap')}
        icon="map"
        variant="outline"
        onPress={() => navigation.navigate(changing ? 'MapPicker' : 'WelcomeMap')}
        style={styles.map}
        testID="pick-on-map"
      />
      <AppText variant="caption" color="textSecondary" style={styles.privacy}>
        {t('onboarding.privacy', { radius: formatDistance(10_000) })}
      </AppText>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  changeHeader: { paddingTop: t.spacing.sm, paddingHorizontal: t.spacing.xs, flexDirection: 'row' },
  reason: { marginTop: t.spacing.sm, marginBottom: t.spacing.xxl },
  notice: {
    flexDirection: 'row',
    gap: t.spacing.sm,
    alignItems: 'flex-start',
    backgroundColor: t.colors.surfaceRose,
    borderRadius: t.radii.pill,
    padding: t.spacing.md,
    marginTop: t.spacing.md,
  },
  flex: { flex: 1 },
  or: { marginTop: t.spacing.xxl, marginBottom: t.spacing.sm },
  map: { marginTop: t.spacing.xl },
  privacy: { marginTop: t.spacing.lg },
}));
