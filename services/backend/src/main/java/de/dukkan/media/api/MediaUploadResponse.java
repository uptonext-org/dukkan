package de.dukkan.media.api;

import de.dukkan.media.application.StoredMedia;

/** Contract schema {@code MediaUpload}. */
public record MediaUploadResponse(String key, String url, int width, int height, String contentType, long sizeBytes) {

  public static MediaUploadResponse of(StoredMedia media, String url) {
    return new MediaUploadResponse(media.key(), url, media.width(), media.height(), media.contentType(), media.sizeBytes());
  }
}
