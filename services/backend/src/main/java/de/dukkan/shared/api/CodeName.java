package de.dukkan.shared.api;

import org.jspecify.annotations.Nullable;

/** A code with its localised name, e.g. a market type or cuisine (contract schema {@code CodeName}). */
public record CodeName(String code, String name, @Nullable String iconKey) {}
