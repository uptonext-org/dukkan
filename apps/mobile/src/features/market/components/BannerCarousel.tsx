import { FlatList, Image, Pressable, View, useWindowDimensions } from 'react-native';

import type { Banner } from '../../../shared/api';
import { makeStyles, tintColor, useTheme } from '../../../shared/theme';
import { AppText } from '../../../shared/ui';

/**
 * Hero carousel: rounded 16 cards, white overline + title on a 35 % scrim over photos, the next
 * card peeks ~24 pt. Without a photo the card uses its tint and dark text (white on a pastel tint
 * would fail contrast). Starts at the start edge in RTL; snapping is instant with Reduce Motion
 * because there is no auto-advance.
 */
export function BannerCarousel({ banners, onPress, label }: { banners: Banner[]; onPress: (banner: Banner) => void; label: string }) {
  const styles = useStyles();
  const theme = useTheme();
  const { width } = useWindowDimensions();
  const cardWidth = width - theme.layout.gutter * 2 - theme.layout.carouselPeek;
  const gap = theme.spacing.sm;
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
            accessibilityLabel={[item.overline, item.title].filter(Boolean).join(', ')}
            accessibilityHint={label}
            accessibilityValue={{ text: `${index + 1} / ${banners.length}` }}
            style={[styles.card, { width: cardWidth, backgroundColor: tintColor(theme.colors, item.tint) }]}
            testID={`banner-${item.id}`}>
            {hasImage && <Image source={{ uri: item.imageUrl ?? undefined }} style={styles.image} resizeMode="cover" accessibilityIgnoresInvertColors />}
            <View style={[styles.textBox, hasImage && styles.scrim]}>
              {item.overline != null && (
                <AppText variant="callout" style={hasImage ? styles.onPhoto : undefined} numberOfLines={2}>
                  {item.overline}
                </AppText>
              )}
              <AppText variant="bannerTitle" style={hasImage ? styles.onPhoto : undefined} numberOfLines={3}>
                {item.title}
              </AppText>
            </View>
          </Pressable>
        );
      }}
    />
  );
}

const useStyles = makeStyles((t) => ({
  content: { paddingHorizontal: t.layout.gutter },
  card: { aspectRatio: 0.9, borderRadius: t.radii.tile, overflow: 'hidden' },
  image: { position: 'absolute', top: 0, bottom: 0, start: 0, end: 0 },
  textBox: { padding: t.spacing.lg, gap: t.spacing.xs },
  scrim: { backgroundColor: t.colors.scrim },
  onPhoto: { color: t.colors.overlayText },
}));
