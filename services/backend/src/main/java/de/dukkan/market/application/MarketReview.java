package de.dukkan.market.application;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Contract schema {@code MarketReview}: whether a market's seller information is complete enough to
 * approve it. A marketplace must know who sells on it and show it to customers (§ 5 DDG imprint,
 * Art. 30 DSA traceability of traders). {@code required} items block approval; the others are hints
 * for the reviewer (a register entry exists only for registered businesses).
 */
public record MarketReview(boolean complete, List<Item> items) {

  public record Item(String code, boolean ok, boolean required) {}

  private static final Pattern POSTCODE = Pattern.compile("(^|\\D)\\d{5}(\\D|$)");
  private static final Pattern WORDS = Pattern.compile("\\S+\\s+\\S+");

  static MarketReview of(String legalName, String addressLine, @Nullable String phone, @Nullable String registerInfo) {
    List<Item> items =
        List.of(
            // A person's first and last name, or a company name with its legal form.
            new Item("LEGAL_NAME", WORDS.matcher(legalName.strip()).find(), true),
            // A postal address: street and number, postcode and town.
            new Item("POSTAL_ADDRESS", POSTCODE.matcher(addressLine).find(), true),
            new Item("PHONE", phone != null && phone.chars().filter(Character::isDigit).count() >= 6, true),
            new Item("REGISTER_INFO", registerInfo != null && !registerInfo.isBlank(), false));
    return new MarketReview(items.stream().allMatch(i -> i.ok() || !i.required()), items);
  }
}
