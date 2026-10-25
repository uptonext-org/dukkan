package de.dukkan.feedback.application;

import de.dukkan.feedback.infrastructure.FeedbackRepository;
import de.dukkan.feedback.infrastructure.FeedbackRepository.FeedbackRow;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.market.application.MarketService;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer feedback to a market (merchant point 3). Customers can write only while the merchant
 * has switched receiving feedback on; the merchant reads it with the customer's first name.
 */
@Service
public class FeedbackService {

  /** Longest message in characters. */
  public static final int MAX_LENGTH = 1000;

  /** Contract schema {@code FeedbackAvailability}: whether customers can write to the market. */
  public record Availability(boolean enabled, int maxLength) {}

  /** Contract schema {@code MarketFeedback}: one message as the merchant sees it. */
  public record FeedbackView(UUID id, String customerName, @Nullable Integer rating, String message, Instant createdAt) {}

  /** Contract schema {@code MerchantFeedbackPage}: the switch plus the newest messages. */
  public record MerchantFeedback(boolean enabled, PageResponse<FeedbackView> feedback) {}

  /** A message in the account export. */
  public record ExportedFeedback(UUID marketId, @Nullable Integer rating, String message, Instant createdAt) {}

  private final FeedbackRepository feedback;
  private final MarketService markets;
  private final IdentityService identity;
  private final Clock clock;
  private final FixedWindowRateLimiter<String> perMarketAndDay;

  public FeedbackService(FeedbackRepository feedback, MarketService markets, IdentityService identity, Clock clock, RateLimitStore rateLimits) {
    this.feedback = feedback;
    this.markets = markets;
    this.identity = identity;
    this.clock = clock;
    // Against spam: a few messages per customer and market a day are plenty.
    this.perMarketAndDay = new FixedWindowRateLimiter<>("market-feedback", 3, Duration.ofDays(1), clock, rateLimits);
  }

  @Transactional(readOnly = true)
  public Availability availability(UUID marketId) {
    markets.activeInfo(marketId);
    return new Availability(feedback.enabled(marketId), MAX_LENGTH);
  }

  @Transactional
  public void submit(UUID userId, UUID marketId, @Nullable Integer rating, String message) {
    markets.activeInfo(marketId);
    if (!feedback.enabled(marketId)) {
      throw new BusinessRuleException(ErrorCodes.FEEDBACK_DISABLED, "This market does not take feedback at the moment.");
    }
    perMarketAndDay.acquire(userId + ":" + marketId, "feedback messages to this market");
    feedback.insert(UUID.randomUUID(), marketId, userId, rating, message.strip(), clock.instant());
  }

  /** For the market's owner (checked by the caller). */
  @Transactional(readOnly = true)
  public MerchantFeedback forMerchant(UUID marketId, int page, int size) {
    List<FeedbackRow> rows = feedback.page(marketId, page, size);
    Map<UUID, String> names = new HashMap<>();
    List<FeedbackView> items =
        rows.stream()
            .map(r -> new FeedbackView(r.id(), names.computeIfAbsent(r.userId(), id -> identity.getUser(id).firstName()), r.rating(), r.message(), r.createdAt()))
            .toList();
    return new MerchantFeedback(feedback.enabled(marketId), PageResponse.of(items, page, size, feedback.count(marketId)));
  }

  /** For the market's owner (checked by the caller). */
  @Transactional
  public MerchantFeedback setEnabled(UUID marketId, boolean enabled, int size) {
    feedback.setEnabled(marketId, enabled, clock.instant());
    return forMerchant(marketId, 0, size);
  }

  @Transactional(readOnly = true)
  public List<ExportedFeedback> export(UUID userId) {
    return feedback.byUser(userId).stream().map(r -> new ExportedFeedback(r.marketId(), r.rating(), r.message(), r.createdAt())).toList();
  }

  @Transactional
  public void deleteAll(UUID userId) {
    feedback.deleteByUser(userId);
  }
}
