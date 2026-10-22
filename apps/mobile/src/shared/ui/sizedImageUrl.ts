/** Widths the server makes smaller copies at (GET /media/{key}?w=…, B-32d). */
export const IMAGE_WIDTHS = [160, 320, 640, 1080] as const;

/** A Dukkan media URL (content-addressed key at the end, no query yet). */
const MEDIA_URL = /\/[0-9a-f]{64}\.(jpg|png|webp)$/;

/**
 * The URL of the smallest server copy at least `pixels` wide (largest 1080), so lists do not load
 * full-size photos over mobile data. Other URLs (external images) are returned unchanged.
 */
export function sizedImageUrl(uri: string, pixels: number): string {
  if (!MEDIA_URL.test(uri) || !Number.isFinite(pixels) || pixels <= 0) {
    return uri;
  }
  const width = IMAGE_WIDTHS.find((w) => w >= pixels) ?? IMAGE_WIDTHS[IMAGE_WIDTHS.length - 1];
  return `${uri}?w=${String(width)}`;
}
