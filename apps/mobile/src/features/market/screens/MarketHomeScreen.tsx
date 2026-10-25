import { useNavigation, type NavigationProp } from '@react-navigation/native';
import { Pressable, RefreshControl, ScrollView, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketTabScreenProps, RootStackParamList } from '../../../navigation/types';
import type { Banner, HomeSection } from '../../../shared/api';
import { forSpeech } from '../../../shared/format';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, CardSkeletons, ErrorState, Icon, IconButton, SectionTitle } from '../../../shared/ui';
import { ProductCarousel } from '../../catalog/components/ProductCarousel';
import { FulfilmentLink } from '../../fulfilment/components/FulfilmentLink';
import { ShoppingListEntry } from '../../shoppingList/components/ShoppingListEntry';
import { usePlatform } from '../../legal/usePlatform';
import { useMarket, useMarketHome } from '../api/hooks';
import { BannerCarousel } from '../components/BannerCarousel';
import { CategoryBand } from '../components/CategoryBand';
import { MarketFeedbackCard } from '../components/MarketFeedbackCard';
import { MarketInfoCard } from '../components/MarketInfoCard';
import { useMarketContext } from '../MarketContext';

/** Market start page: fulfilment link, category band, banner carousel, product sections, info and feedback cards. */
export function MarketHomeScreen({ navigation }: MarketTabScreenProps<'MarketHome'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { marketId } = useMarketContext();
  const rootNavigation = useNavigation<NavigationProp<RootStackParamList>>();
  const home = useMarketHome(marketId);
  const detail = useMarket(marketId).data;
  const payOnSite = usePlatform().data?.payment === 'ON_SITE';

  if (home.isPending) {
    return <CardSkeletons count={2} height={300} />;
  }
  if (home.isError) {
    return <ErrorState error={home.error} onRetry={() => void home.refetch()} />;
  }
  const { market, banners, sections } = home.data;
  // A showcase market (no online orders right now): no delivery choice and no shopping list.
  const orderable = market.acceptsOrders !== false;

  const openBanner = (banner: Banner) => {
    switch (banner.target.type) {
      case 'PRODUCT':
        if (banner.target.id != null) {
          navigation.navigate('MarketProduct', { productId: banner.target.id });
        }
        return;
      case 'CATEGORY':
        navigation.navigate('MarketProducts', { title: banner.title, filter: banner.target.id != null ? { categoryId: banner.target.id } : {} });
        return;
      case 'PROMOTIONS':
        navigation.navigate('MarketProducts', { title: t('marketSearch.allOffers'), filter: { promotedOnly: true } });
        return;
      case 'NEW':
        navigation.navigate('MarketProducts', { title: t('marketSearch.newInMarket'), filter: { newOnly: true } });
        return;
    }
  };

  const openSection = (section: HomeSection) => {
    switch (section.key) {
      case 'PROMOTIONS':
        navigation.navigate('MarketProducts', { title: section.title, filter: { promotedOnly: true } });
        return;
      case 'NEW':
        navigation.navigate('MarketProducts', { title: section.title, filter: { newOnly: true } });
        return;
      case 'BESTSELLERS':
        navigation.navigate('MarketProducts', { title: section.title, filter: { bestsellersOnly: true } });
        return;
      case 'FREQUENTLY_ORDERED':
        navigation.navigate('MarketFavorites');
        return;
      case 'CATEGORY':
        navigation.navigate('MarketProducts', { title: section.title, filter: section.categoryId != null ? { categoryId: section.categoryId } : {} });
        return;
    }
  };

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={home.isRefetching} onRefresh={() => void home.refetch()} tintColor={colors.primary} />}
      testID="market-home">
      <View style={styles.topRow}>
        {orderable ? <FulfilmentLink market={market} /> : <View />}
        <IconButton icon="user" label={t('shell.account')} onPress={() => rootNavigation.navigate('Shell', { screen: 'Profile' })} />
      </View>
      <CategoryBand marketId={marketId} onOpen={(category) => navigation.navigate('MarketProducts', { title: category.name, filter: { categoryId: category.id } })} />
      {banners.length > 0 && <BannerCarousel banners={banners} onPress={openBanner} label={t('marketHome.banners')} />}
      {orderable && (
        <View style={styles.gutter}>
          <ShoppingListEntry onPress={() => navigation.navigate('ShoppingList')} />
        </View>
      )}
      {sections
        .filter((section) => section.items.length > 0)
        .map((section) => (
          <View key={`${section.key}-${section.categoryId ?? ''}`} style={styles.section} testID={`home-section-${section.key}`}>
            <View style={styles.gutter}>
              <SectionTitle
                title={section.title}
                subtitle={section.subtitle ?? null}
                trailing={
                  <Pressable
                    onPress={() => openSection(section)}
                    accessibilityRole="button"
                    accessibilityLabel={forSpeech(t('marketHome.showAll', { title: section.title }))}
                    style={styles.all}>
                    <AppText variant="callout" color="primary" dense>
                      {t('marketHome.all')}
                    </AppText>
                    <Icon name="chevron" size={16} color={colors.primary} />
                  </Pressable>
                }
              />
            </View>
            <ProductCarousel marketId={marketId} products={section.items} label={section.title} />
          </View>
        ))}
      {/* Opening hours, delivery, minimum order and payment last, below the products. */}
      <View style={[styles.gutter, styles.bottom]}>
        <MarketInfoCard market={market} paymentMethods={payOnSite && orderable ? detail?.paymentMethods : undefined} />
        <MarketFeedbackCard marketId={marketId} marketName={market.name} />
      </View>
    </ScrollView>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  content: { paddingBottom: t.spacing.xxxl, gap: t.spacing.xxl },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingStart: t.layout.gutter, paddingEnd: t.spacing.xs },
  gutter: { paddingHorizontal: t.layout.gutter },
  bottom: { gap: t.spacing.lg },
  section: { gap: 0 },
  all: { flexDirection: 'row', alignItems: 'center', minHeight: 44, gap: 2 },
}));
