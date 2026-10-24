package de.dukkan.market.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code MarketDetail} (MarketSummary plus profile fields). */
public record MarketDetail(
    @JsonUnwrapped MarketSummary summary,
    @Nullable String description,
    @Nullable String phone,
    List<OpeningHours> openingHours,
    String primaryLocale,
    Imprint imprint,
    List<OrderSettings.PaymentMethod> paymentMethods) {

  /** Legal imprint. */
  public record Imprint(String legalName, String addressLine, @Nullable String registerInfo) {}
}
