package de.dukkan.i18n.application;

/** An enabled content locale (contract schema {@code Locale}). */
public record SupportedLocale(String code, String nativeName, String direction) {}
