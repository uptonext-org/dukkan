package de.dukkan.media.application;

/** The result of an accepted upload; the API layer turns {@code key} into a public URL. */
public record StoredMedia(String key, String contentType, int width, int height, long sizeBytes) {}
