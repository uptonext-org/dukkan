package de.dukkan.media.application;

import org.springframework.core.io.Resource;

/** A stored file, ready to be streamed. */
public record MediaObject(String key, String contentType, long sizeBytes, Resource content) {}
