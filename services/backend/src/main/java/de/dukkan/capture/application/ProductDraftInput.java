package de.dukkan.capture.application;

import org.jspecify.annotations.Nullable;

/** Contract schema {@code ProductDraftRequest}: at least one of both is set. */
public record ProductDraftInput(@Nullable String text, @Nullable String imageKey) {}
