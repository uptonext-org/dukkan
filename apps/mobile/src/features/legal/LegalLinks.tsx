import { Linking, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { makeStyles } from '../../shared/theme';
import { AppText, ListRow } from '../../shared/ui';
import { usePlatform } from './usePlatform';

/** Where the shared product catalog comes from; its licence (ODbL) requires naming it. */
const PRODUCT_DATA_URL = 'https://world.openfoodfacts.org';

/**
 * Imprint, privacy notice, terms and support, as the operator publishes them (GET /platform),
 * and the source of the product data (always shown: the ODbL requires the attribution).
 */
export function LegalLinks() {
  const styles = useStyles();
  const { t } = useTranslation();
  const platform = usePlatform().data;
  const imprintUrl = platform?.legal.imprintUrl ?? null;
  const privacyUrl = platform?.legal.privacyUrl ?? null;
  const termsUrl = platform?.legal.termsUrl ?? null;
  const supportEmail = platform?.supportEmail ?? null;
  const open = (url: string) => () => void Linking.openURL(url).catch(() => undefined);
  return (
    <View style={styles.section} testID="legal-links">
      <AppText variant="subhead" color="textSecondary" accessibilityRole="header" style={styles.heading}>
        {t('legal.title')}
      </AppText>
      {supportEmail !== null && (
        <ListRow title={t('legal.support')} subtitle={supportEmail} icon="mail" onPress={open(`mailto:${supportEmail}`)} testID="legal-support" />
      )}
      {termsUrl !== null && <ListRow title={t('legal.terms')} icon="document" onPress={open(termsUrl)} testID="legal-terms" />}
      {privacyUrl !== null && <ListRow title={t('legal.privacy')} icon="shield" onPress={open(privacyUrl)} testID="legal-privacy" />}
      {imprintUrl !== null && <ListRow title={t('legal.imprint')} icon="document" onPress={open(imprintUrl)} testID="legal-imprint" />}
      <ListRow title={t('legal.productData')} subtitle={t('legal.productDataSource')} icon="document" onPress={open(PRODUCT_DATA_URL)} testID="legal-product-data" />
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  section: { marginTop: t.spacing.xl },
  heading: { marginBottom: t.spacing.xs },
}));
