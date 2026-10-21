import { useState } from 'react';
import { View } from 'react-native';
import MapView, { type Region } from 'react-native-maps';
import { useTranslation } from 'react-i18next';

import type { RootStackScreenProps } from '../../../navigation/types';
import { useApi } from '../../../shared/api';
import { HAMBURG, toChosenPlace, useChosenPlace } from '../../../shared/location';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, Screen, ScreenHeader } from '../../../shared/ui';

/** Map with a fixed centre pin: move the map under the pin, then confirm. */
export function MapPickerScreen({ navigation, route }: RootStackScreenProps<'MapPicker' | 'WelcomeMap'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const api = useApi();
  const { place, setPlace } = useChosenPlace();
  const start = place?.location ?? HAMBURG;
  const [region, setRegion] = useState<Region>({ latitude: start.lat, longitude: start.lng, latitudeDelta: 0.03, longitudeDelta: 0.03 });
  const [saving, setSaving] = useState(false);

  const confirm = async () => {
    setSaving(true);
    const point = { lat: region.latitude, lng: region.longitude };
    let chosen;
    try {
      chosen = toChosenPlace(await api.reverseGeocode(point.lat, point.lng));
    } catch {
      const label = t('onboarding.pinnedLocation');
      chosen = { label, shortLabel: label, location: point };
    }
    await setPlace(chosen);
    setSaving(false);
    // On first launch the navigator switches to the shell by itself; when changing, go back to it.
    if (route.name === 'MapPicker') {
      navigation.popTo('Shell');
    }
  };

  return (
    <Screen
      padded={false}
      header={<ScreenHeader title={t('onboarding.mapTitle')} onBack={() => navigation.goBack()} />}
      footer={<Button title={t('onboarding.confirmPin')} onPress={() => void confirm()} loading={saving} testID="confirm-pin" />}>
      <View style={styles.flex}>
        <MapView
          style={styles.flex}
          initialRegion={region}
          onRegionChangeComplete={setRegion}
          accessibilityLabel={t('onboarding.mapLabel')}
          testID="map-picker"
        />
        <View style={styles.pinWrap} pointerEvents="none">
          <Icon name="pin" size={40} color={colors.accent} filled />
        </View>
        <View style={styles.coords} pointerEvents="none">
          <AppText variant="caption" dense>
            {t('onboarding.mapHint')}
          </AppText>
        </View>
      </View>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  flex: { flex: 1 },
  pinWrap: { position: 'absolute', top: 0, bottom: 40, start: 0, end: 0, alignItems: 'center', justifyContent: 'center' },
  coords: { position: 'absolute', top: t.spacing.md, start: t.layout.gutter, end: t.layout.gutter, alignItems: 'center', backgroundColor: t.colors.surface, borderRadius: t.radii.pill, paddingHorizontal: t.spacing.md, paddingVertical: 4 },
}));
