package de.dukkan.capture.domain;

import java.util.List;

/** The 14 allergens that EU Regulation 1169/2011 (Annex II) requires to be declared. */
public final class Allergens {

  public static final List<String> CODES =
      List.of(
          "gluten",
          "crustaceans",
          "eggs",
          "fish",
          "peanuts",
          "soy",
          "milk",
          "nuts",
          "celery",
          "mustard",
          "sesame",
          "sulphites",
          "lupin",
          "molluscs");

  private Allergens() {}
}
