import { FlatList, Image, Pressable, View, useWindowDimensions } from 'react-native';

import type { Banner } from '../../../shared/api';
import { forSpeech } from '../../../shared/format';
import { makeStyles, tintColor, useTheme } from '../../../shared/theme';
import { AppText, Icon, type IconName } from '../../../shared/ui';

/** Banners without a photo are compact: about 16:9, at most this tall. */
const MAX_PLAIN_HEIGHT = 190;

/** Decorative icon trio per banner target: our own composition on the tint, no third-party art. */
function illustrationIcons(banner: Banner): [IconName, IconName, IconName] {
  switch (banner.target.type) {
    case 'PROMOTIONS':
      return ['percent', 'tag', 'sparkles'];
    case 'NEW':
      return ['sparkles', 'package', 'leaf'];
    case 'CATEGORY':
      return ['basket', 'leaf', 'package'];
    default:
      return ['package', 'heart', 'sparkles'];
  }
}

/**
 * Decorative illustration for a banner without a photo: three soft circles in `surface` with
 * line icons, tilted like stickers, at the end side of the card. Hidden from screen readers.
 */
function BannerIllustration({ banner }: { banner: Banner }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const [big, small, tiny] = illustrationIcons(banner);
  const accent = banner.target.type === 'PROMOTIONS' ? colors.accent : colors.primary;
  return (
    <View style={styles.illustration} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none">
      <View style={[styles.blob, styles.blobBig]}>
        <Icon name={big} size={34} color={accent} strokeWidth={1.8} />
      </View>
      <View style={[styles.blob, styles.blobSmall]}>
        <Icon name={small} size={22} color={colors.taste} strokeWidth={2} />
      </View>
      <View style={[styles.blob, styles.blobTiny]}>
        <Icon name={tiny} size={16} color={colors.primary} strokeWidth={2} />
      </View>
    </View>
  );
}

/**
 * Hero carousel: rounded 16 cards, the next card peeks ~24 pt. With a photo: the photo, a 35 %
 * scrim and white overline + title. Without a photo the card is compact (~16:9, ≤ 190 pt) on its
 * tint with a small decorative illustration and dark text (white on a pastel tint would fail
 * contrast). Starts at the start edge in RTL; no auto-advance, so Reduce Motion needs nothing.
 */
export function BannerCarousel({ banners, onPress, label }: { banners: Banner[]; onPress: (banner: Banner) => void; label: string }) {
  const styles = useStyles();
  const theme = useTheme();
  const { width } = useWindowDimensions();
  const cardWidth = width - theme.layout.gutter * 2 - theme.layout.carouselPeek;
  const gap = theme.spacing.sm;
  const anyImage = banners.some((b) => b.imageUrl != null && b.imageUrl !== '');
  // One height for the row: photo banners keep their portrait card, plain ones are compact.
  const cardHeight = anyImage ? Math.round(cardWidth / 0.9) : Math.min(MAX_PLAIN_HEIGHT, Math.round((cardWidth * 9) / 16));
  return (
    <FlatList
      horizontal
      data={banners}
      keyExtractor={(b) => b.id}
      showsHorizontalScrollIndicator={false}
      snapToInterval={cardWidth + gap}
      decelerationRate="fast"
      contentContainerStyle={[styles.content, { gap }]}
      accessibilityLabel={label}
      testID="banner-carousel"
      renderItem={({ item, index }) => {
        const hasImage = item.imageUrl != null && item.imageUrl !== '';
        return (
          <Pressable
            onPress={() => onPress(item)}
            accessibilityRole="button"
            accessibilityLabel={forSpeech([item.overline, item.title].filter(Boolean).join(', '))}
            accessibilityHint={label}
            accessibilityValue={{ text: `${index + 1} / ${banners.length}` }}
            style={[styles.card, { width: cardWidth, height: cardHeight, backgroundColor: tintColor(theme.colors, item.tint) }]}
            testID={`banner-${item.id}`}>
            {hasImage ? (
              <>
                <Image source={{ uri: item.imageUrl ?? undefined }} style={styles.image} resizeMode="cover" accessibilityIgnoresInvertColors />
                <View style={[styles.textBox, styles.scrim]}>
                  {item.overline != null && (
                    <AppText variant="callout" style={styles.onPhoto} numberOfLines={2}>
                      {item.overline}
                    </AppText>
                  )}
                  <AppText variant="bannerTitle" style={styles.onPhoto} numberOfLines={3}>
                    {item.title}
                  </AppText>
                </View>
              </>
            ) : (
              <View style={styles.plain}>
                <BannerIllustration banner={item} />
                <View style={styles.plainText}>
                  {item.overline != null && (
                    <AppText variant="callout" numberOfLines={1}>
                      {item.overline}
                    </AppText>
                  )}
                  <AppText variant="title" numberOfLines={3}>
                    {item.title}
                  </AppText>
                </View>
              </View>
            )}
          </Pressable>
        );
      }}
    />
  );
}

const useStyles = makeStyles((t) => ({
  content: { paddingHorizontal: t.layout.gutter },
  card: { borderRadius: t.radii.tile, overflow: 'hidden' },
  image: { position: 'absolute', top: 0, bottom: 0, start: 0, end: 0 },
  textBox: { padding: t.spacing.lg, gap: t.spacing.xs },
  scrim: { backgroundColor: t.colors.scrim },
  onPhoto: { color: t.colors.overlayText },
  plain: { flex: 1, padding: t.spacing.lg, justifyContent: 'flex-end' },
  plainText: { gap: 2, maxWidth: '82%' },
  // Top-end corner only, above the text block, so it never runs under the title.
  illustration: { position: 'absolute', top: 0, end: 0, width: 150, height: 92 },
  blob: { position: 'absolute', backgroundColor: t.colors.surface, alignItems: 'center', justifyContent: 'center', borderRadius: t.radii.full },
  blobBig: { width: 64, height: 64, top: 14, end: 16, transform: [{ rotate: '-8deg' }] },
  blobSmall: { width: 42, height: 42, top: 14, end: 90, transform: [{ rotate: '10deg' }] },
  blobTiny: { width: 30, height: 30, top: 58, end: 84, transform: [{ rotate: '-12deg' }] },
}));
