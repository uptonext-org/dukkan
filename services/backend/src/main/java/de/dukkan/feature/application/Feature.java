package de.dukkan.feature.application;

/**
 * A function platform admins switch on and off for all customers. The shopping list starts
 * switched off; admins turn it on, then optionally speaking and photo/video on top.
 */
public enum Feature {
  /** Shopping list in a market: type or paste a list, matched to products. */
  SHOPPING_LIST(false),
  /** Speak the shopping list (speech recognition on the device). Needs SHOPPING_LIST. */
  SHOPPING_LIST_VOICE(false),
  /** Photos or a short video of a handwritten list, read by the AI model. Needs SHOPPING_LIST. */
  SHOPPING_LIST_MEDIA(false),
  /**
   * Merchants enter texts (market description, products, promotions) in every language. Off:
   * only in the market's language, which customers then see whatever language they use.
   */
  MERCHANT_MULTILINGUAL(false);

  private final boolean defaultEnabled;

  Feature(boolean defaultEnabled) {
    this.defaultEnabled = defaultEnabled;
  }

  public boolean defaultEnabled() {
    return defaultEnabled;
  }
}
