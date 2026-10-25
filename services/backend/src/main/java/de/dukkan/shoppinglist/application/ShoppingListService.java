package de.dukkan.shoppinglist.application;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.MarketProductQuery;
import de.dukkan.catalog.application.ProductSummary;
import de.dukkan.feature.application.Feature;
import de.dukkan.feature.application.FeatureService;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.market.application.MarketService;
import de.dukkan.shared.ai.AiBudget;
import de.dukkan.shared.ai.ModelImages;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import de.dukkan.shoppinglist.domain.ListItem;
import de.dukkan.shoppinglist.domain.MatchRanking;
import de.dukkan.shoppinglist.domain.PackCount;
import de.dukkan.shoppinglist.domain.SimpleListParser;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Shopping list by voice, photo or video (F1): the app sends what the customer said (transcribed on
 * the device) and/or photos or video frames; each item is matched to an available product of the
 * market with the number of units to add. Nothing is stored and nothing is added here: the customer
 * confirms in the app, which then fills the cart.
 *
 * <p>With a language model, text and images are read by it; without one, or when it fails, text is
 * read by {@link SimpleListParser}. Images need the model.
 */
@Service
public class ShoppingListService {

  public static final int MAX_TEXT = 2000;
  public static final int MAX_IMAGES = 4;
  static final int ALTERNATIVES = 2;
  /** Search hits looked at per term before ranking them for a list ({@link MatchRanking}). */
  static final int CANDIDATES = 10;

  public enum Engine {
    AI,
    RULES
  }

  /** A product with the number of units that covers the item. */
  public record Choice(ProductSummary product, int quantity) {}

  /** One item: the customer's words, the amount as a label ("1 kg"), the best match and others. */
  public record Line(String text, @Nullable String amount, @Nullable Choice match, List<Choice> alternatives) {}

  public record Result(Engine engine, List<Line> lines) {}

  private final ShoppingListReader reader;
  private final MarketService markets;
  private final CatalogService catalog;
  private final FixedWindowRateLimiter<UUID> rateLimiter;
  private final IdentityService identity;
  private final AiBudget budget;
  private final FeatureService features;

  public ShoppingListService(
      ShoppingListReader reader,
      MarketService markets,
      CatalogService catalog,
      IdentityService identity,
      AiBudget budget,
      FeatureService features,
      DukkanProperties properties,
      Clock clock,
      RateLimitStore store) {
    this.reader = reader;
    this.markets = markets;
    this.catalog = catalog;
    this.identity = identity;
    this.budget = budget;
    this.features = features;
    this.rateLimiter = new FixedWindowRateLimiter<>("shopping-lists", properties.shoppingList().requestsPerHour(), Duration.ofHours(1), clock, store);
  }

  public boolean aiAvailable() {
    return reader.enabled();
  }

  /** What admins switched on for customers (the app shows only these options). */
  public record Switches(boolean enabled, boolean voice, boolean media) {}

  public Switches switches() {
    Map<Feature, Boolean> all = features.all();
    return new Switches(all.get(Feature.SHOPPING_LIST), all.get(Feature.SHOPPING_LIST_VOICE), all.get(Feature.SHOPPING_LIST_MEDIA));
  }

  public Result read(UUID userId, UUID marketId, @Nullable String text, List<ModelImages.Image> images, ContentLocale locale) {
    Switches switches = switches();
    if (!switches.enabled() || (!images.isEmpty() && !switches.media())) {
      throw new BusinessRuleException(ErrorCodes.FEATURE_DISABLED, "This function is switched off.");
    }
    String spoken = text == null || text.isBlank() ? null : text.strip();
    if (spoken == null && images.isEmpty()) {
      throw new InvalidRequestException("text", "NotBlank", "send a text or at least one image");
    }
    if (spoken != null && spoken.length() > MAX_TEXT) {
      throw new InvalidRequestException("text", "Size", "at most " + MAX_TEXT + " characters");
    }
    if (images.size() > MAX_IMAGES) {
      throw new InvalidRequestException("images", "Size", "at most " + MAX_IMAGES + " images");
    }
    if (!images.isEmpty() && !reader.enabled()) {
      throw new BusinessRuleException(ErrorCodes.SHOPPING_LIST_AI_UNAVAILABLE, "Reading photos and videos is not set up.");
    }
    markets.activeInfo(marketId);
    rateLimiter.acquire(userId, "shopping-lists");
    // The language model only for confirmed addresses (throwaway accounts would make it a free proxy)
    // and within the platform's hourly budget; typed and spoken lists then use the simple reader.
    boolean verified = identity.isEmailVerified(userId);
    if (!images.isEmpty() && !verified) {
      throw new BusinessRuleException(ErrorCodes.EMAIL_NOT_VERIFIED, "Please confirm your email address first (link in the welcome email).");
    }
    boolean useModel = reader.enabled() && verified && budget.tryAcquire();
    if (!images.isEmpty() && !useModel) {
      throw new BusinessRuleException(ErrorCodes.SHOPPING_LIST_AI_UNAVAILABLE, "Photos cannot be read right now. Please try again later.");
    }

    List<ModelImages.Image> prepared = images.stream().map(ModelImages::prepare).flatMap(Optional::stream).toList();
    Optional<List<ListItem>> read =
        useModel && (spoken != null || !prepared.isEmpty())
            ? reader.read(new ShoppingListReader.Request(spoken, prepared, locale.effective()))
            : Optional.empty();
    if (read.isEmpty() && spoken == null) {
      throw new BusinessRuleException(ErrorCodes.SHOPPING_LIST_AI_UNAVAILABLE, "The photos could not be read. Please try again.");
    }
    Engine engine = read.isPresent() ? Engine.AI : Engine.RULES;
    List<ListItem> items = read.orElseGet(() -> SimpleListParser.parse(spoken));
    List<Line> lines = new ArrayList<>();
    for (ListItem item : items.subList(0, Math.min(items.size(), SimpleListParser.MAX_ITEMS))) {
      lines.add(match(marketId, item, locale, userId));
    }
    return new Result(engine, lines);
  }

  private Line match(UUID marketId, ListItem item, ContentLocale locale, UUID userId) {
    List<ProductSummary> found = List.of();
    for (String term : item.searchTerms()) {
      found =
          catalog
              .searchMarket(marketId, new MarketProductQuery(term, null, false, false, false, false, "relevance", 0, CANDIDATES), locale, userId)
              .items()
              .stream()
              .filter(ProductSummary::available)
              .sorted(Comparator.comparingInt(p -> MatchRanking.score(term, p.name())))
              .limit(ALTERNATIVES + 1L)
              .toList();
      if (!found.isEmpty()) {
        break;
      }
    }
    List<Choice> choices = found.stream().map(p -> new Choice(p, PackCount.of(item.amount(), p.unitSize(), p.maxOrderQuantity()))).toList();
    return new Line(item.text(), item.amount().label(), choices.isEmpty() ? null : choices.getFirst(), choices.isEmpty() ? List.of() : choices.subList(1, choices.size()));
  }
}
