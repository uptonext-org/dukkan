package de.dukkan.media.domain;

/** What an accepted upload turned out to be. */
public record ImageInfo(ImageFormat format, int width, int height) {

  public long pixels() {
    return (long) width * height;
  }
}
