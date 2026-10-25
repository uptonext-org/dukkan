package de.dukkan.promotion.application;

import static java.util.Objects.requireNonNull;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.UiTexts;
import de.dukkan.promotion.infrastructure.PromotionRepository;
import de.dukkan.promotion.infrastructure.PromotionRepository.PromotionRow;
import de.dukkan.promotion.infrastructure.PromotionRepository.RuleRow;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import de.dukkan.shared.time.DukkanTime;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Market promotions. At most one promotion applies per listing: the most recently created running
 * one. Running means {@code validFrom <= now < validUntil}, on one of its weekdays and within its
 * daily window (Berlin time), when it has those.
 */
@Service
@Transactional(readOnly = true)
public class PromotionService {

  /** Most quantity prices of one multi-buy. */
  public static final int MAX_TIERS = 5;

  private final PromotionRepository promotions;
  private final UiTexts texts;
  private final LocaleService locales;
  private final Clock clock;

  public PromotionService(PromotionRepository promotions, UiTexts texts, LocaleService locales, Clock clock) {
    this.promotions = promotions;
    this.texts = texts;
    this.locales = locales;
    this.clock = clock;
  }

  /** The winning promotion rule per listing (listings without one are absent). */
  public Map<UUID, PromotionRule> winningRules(Collection<UUID> listingIds) {
    if (listingIds.isEmpty()) {
      return Map.of();
    }
    return winnersByListing(promotions.findActiveRules(Set.copyOf(listingIds), clock.instant()));
  }

  /** Resolves overlaps: rows must be ordered newest promotion first; the first rule per listing wins. */
  static Map<UUID, PromotionRule> winnersByListing(List<RuleRow> newestFirst) {
    Map<UUID, PromotionRule> winners = new HashMap<>();
    for (RuleRow row : newestFirst) {
      winners.putIfAbsent(row.listingId(), rule(row));
    }
    return winners;
  }

  public Set<UUID> activeListingIds(UUID marketId) {
    return promotions.findActiveListingIds(marketId, clock.instant());
  }

  public Map<UUID, Integer> activeCounts(Collection<UUID> marketIds) {
    return marketIds.isEmpty() ? Map.of() : promotions.countActive(Set.copyOf(marketIds), clock.instant());
  }

  public Set<UUID> marketsWithActivePromotions() {
    return promotions.findMarketsWithActive(clock.instant());
  }

  public List<PromotionView> listActive(UUID marketId, ContentLocale locale, @Nullable String primaryLocale) {
    return promotions.findByMarket(marketId, clock.instant(), true).stream()
        .map(p -> view(p, locale, primaryLocale))
        .toList();
  }

  public List<PromotionView> listAll(UUID marketId, ContentLocale locale, @Nullable String primaryLocale) {
    return promotions.findByMarket(marketId, clock.instant(), false).stream()
        .map(p -> view(p, locale, primaryLocale))
        .toList();
  }

  @Transactional
  public PromotionView create(UUID marketId, PromotionDraft draft, ContentLocale locale, @Nullable String primaryLocale) {
    validate(draft);
    UUID id = UUID.randomUUID();
    promotions.insert(id, marketId, draft, clock.instant());
    return promotions.findByMarket(marketId, clock.instant(), false).stream()
        .filter(p -> p.id().equals(id))
        .findFirst()
        .map(p -> view(p, locale, primaryLocale))
        .orElseThrow();
  }

  /** Ends a promotion now; fails with {@code NOT_FOUND} if it does not belong to the market. */
  @Transactional
  public void end(UUID marketId, UUID promotionId) {
    if (promotions.end(marketId, promotionId, clock.instant()) == 0) {
      throw new NotFoundException("Promotion not found.");
    }
  }

  /** Localised badge text, e.g. "20 % Rabatt" or "3 für 5,00 €" (prices always in de-DE format). */
  public String badgeLabel(PromotionRule rule, ContentLocale locale) {
    return switch (rule) {
      case PromotionRule.PercentOff p ->
          texts.format("badge.PERCENT_OFF", locale, Map.of("percent", Integer.toString(p.percentOff())));
      case PromotionRule.MultiBuy m ->
          String.join(
              " · ",
              m.tiers().stream()
                  .map(t -> texts.format("badge.MULTI_BUY", locale, Map.of("quantity", Integer.toString(t.quantity()), "price", formatEuro(t.priceCents()))))
                  .toList());
      case PromotionRule.FixedPrice f -> texts.format("badge.FIXED_PRICE", locale, Map.of("price", formatEuro(f.offerPriceCents())));
    };
  }

  /** {@code 1234} → {@code 12,34 €}. */
  public static String formatEuro(long cents) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return format.format(cents / 100.0) + " €";
  }

  private void validate(PromotionDraft draft) {
    for (String code : draft.title().keySet()) {
      if (!locales.isEnabled(code)) {
        throw new InvalidRequestException("title", "SupportedLocale", "contains a locale that is not enabled: " + code);
      }
    }
    for (String code : draft.description().keySet()) {
      if (!locales.isEnabled(code)) {
        throw new InvalidRequestException("description", "SupportedLocale", "contains a locale that is not enabled: " + code);
      }
    }
    if (!draft.validUntil().isAfter(draft.validFrom())) {
      throw new InvalidRequestException("validUntil", "AfterValidFrom", "must be after validFrom");
    }
    if (!draft.validUntil().isAfter(clock.instant())) {
      throw new InvalidRequestException("validUntil", "Future", "must be in the future");
    }
    validateSchedule(draft);
    switch (draft.type()) {
      case "PERCENT_OFF" -> {
        if (draft.percentOff() == null) {
          throw new InvalidRequestException("percentOff", "NotNull", "is required for PERCENT_OFF");
        }
        if (!draft.tiers().isEmpty() || !draft.offerPrices().isEmpty()) {
          throw new InvalidRequestException("tiers", "Null", "must be empty for PERCENT_OFF");
        }
      }
      case "MULTI_BUY" -> {
        if (draft.tiers().isEmpty()) {
          throw new InvalidRequestException("tiers", "NotNull", "at least one quantity price is required for MULTI_BUY");
        }
        if (draft.tiers().size() > MAX_TIERS) {
          throw new InvalidRequestException("tiers", "Size", "at most " + MAX_TIERS + " quantity prices");
        }
        if (draft.tiers().stream().map(PromotionRule.Tier::quantity).distinct().count() != draft.tiers().size()) {
          throw new InvalidRequestException("tiers", "DistinctQuantities", "each quantity only once");
        }
        if (draft.percentOff() != null || !draft.offerPrices().isEmpty()) {
          throw new InvalidRequestException("percentOff", "Null", "must be empty for MULTI_BUY");
        }
      }
      case "FIXED_PRICE" -> {
        if (draft.offerPrices().isEmpty() || !draft.offerPrices().keySet().equals(Set.copyOf(draft.listingIds()))) {
          throw new InvalidRequestException("offers", "NotNull", "an offer price for every product is required for FIXED_PRICE");
        }
        if (draft.percentOff() != null || !draft.tiers().isEmpty()) {
          throw new InvalidRequestException("percentOff", "Null", "must be empty for FIXED_PRICE");
        }
      }
      default -> throw new InvalidRequestException("type", "UnknownCode", "must be PERCENT_OFF, MULTI_BUY or FIXED_PRICE");
    }
  }

  private static void validateSchedule(PromotionDraft draft) {
    if (draft.weekdays() != null && (draft.weekdays().isEmpty() || draft.weekdays().stream().anyMatch(d -> d < 1 || d > 7))) {
      throw new InvalidRequestException("weekdays", "Weekdays", "1 (Monday) to 7 (Sunday), at least one day");
    }
    if ((draft.dailyFrom() == null) != (draft.dailyUntil() == null)) {
      throw new InvalidRequestException("dailyUntil", "DailyWindow", "dailyFrom and dailyUntil must be given together");
    }
    if (draft.dailyFrom() != null && !requireNonNull(draft.dailyUntil()).isAfter(draft.dailyFrom())) {
      throw new InvalidRequestException("dailyUntil", "DailyWindow", "must be after dailyFrom on the same day");
    }
  }

  private static PromotionRule rule(RuleRow row) {
    return switch (row.type()) {
      case "PERCENT_OFF" -> new PromotionRule.PercentOff(row.promotionId(), requireNonNull(row.percentOff()));
      case "MULTI_BUY" -> new PromotionRule.MultiBuy(row.promotionId(), row.tiers());
      case "FIXED_PRICE" -> new PromotionRule.FixedPrice(row.promotionId(), requireNonNull(row.offerPriceCents()));
      default -> throw new IllegalStateException("Unknown promotion type " + row.type());
    };
  }

  private static PromotionView view(PromotionRow p, ContentLocale locale, @Nullable String primaryLocale) {
    String title = locale.pick(p.titles(), primaryLocale);
    return new PromotionView(
        p.id(),
        p.marketId(),
        title == null ? "" : title,
        locale.pick(p.descriptions(), primaryLocale),
        p.type(),
        p.percentOff(),
        p.tiers().isEmpty() ? null : p.tiers().getFirst().quantity(),
        p.tiers().isEmpty() ? null : Money.eur(p.tiers().getFirst().priceCents()),
        p.tiers().stream().map(t -> new PromotionView.TierView(t.quantity(), Money.eur(t.priceCents()))).toList(),
        p.listings().entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(e -> new PromotionView.OfferView(e.getKey(), Money.eur(requireNonNull(e.getValue()))))
            .toList(),
        DukkanTime.toApi(p.validFrom()),
        DukkanTime.toApi(p.validUntil()),
        p.weekdays(),
        p.dailyFrom() == null ? null : p.dailyFrom().toString(),
        p.dailyUntil() == null ? null : p.dailyUntil().toString(),
        List.copyOf(p.listings().keySet()));
  }
}
