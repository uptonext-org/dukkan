package de.dukkan.privacy.application;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.cart.application.CartService;
import de.dukkan.favorite.application.FavoriteService;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.UserView;
import de.dukkan.market.application.MarketService;
import de.dukkan.notification.application.NotificationService;
import de.dukkan.notification.application.NotificationService.ExportedNotification;
import de.dukkan.order.application.OrderService;
import de.dukkan.order.application.OrderView;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The data subject's rights for an account (B-51, GDPR Art. 15, 17 and 20): a machine-readable
 * export of everything stored about the user, and deletion from within the app (also required by
 * the App Store and Google Play).
 *
 * <p>Deletion removes addresses, carts (releasing slot reservations), favourites, devices, the inbox
 * and settings, ends every session and anonymises the account row. Past orders stay for the
 * markets' records (commercial and tax retention), without the customer's name, street, house
 * number, delivery notes and coordinates. Accounts with unfinished orders or own markets cannot be
 * deleted yet; the answer says which.
 */
@Service
@Transactional
public class AccountService {

  /** Contract schema {@code AccountExport}. */
  public record AccountExport(
      Instant exportedAt,
      UserView account,
      List<String> linkedSignIns,
      List<AddressView> addresses,
      List<OrderView> orders,
      FavoriteService.FavoriteIds favorites,
      List<ExportedNotification> notifications,
      boolean pushEnabled,
      List<UUID> ownedMarketIds) {}

  private final IdentityService identity;
  private final AddressService addresses;
  private final CartService carts;
  private final FavoriteService favorites;
  private final OrderService orders;
  private final NotificationService notifications;
  private final MarketService markets;
  private final Clock clock;
  private final FixedWindowRateLimiter<UUID> deletionAttempts;
  private final ErasureJournal journal;

  public AccountService(
      IdentityService identity,
      AddressService addresses,
      CartService carts,
      FavoriteService favorites,
      OrderService orders,
      NotificationService notifications,
      MarketService markets,
      Clock clock,
      RateLimitStore rateLimits,
      ErasureJournal journal) {
    this.identity = identity;
    this.addresses = addresses;
    this.carts = carts;
    this.favorites = favorites;
    this.orders = orders;
    this.notifications = notifications;
    this.markets = markets;
    this.clock = clock;
    this.journal = journal;
    // The password check must not become a way to guess passwords with a stolen session.
    this.deletionAttempts = new FixedWindowRateLimiter<>("account-deletion", 5, Duration.ofHours(1), clock, rateLimits);
  }

  @Transactional(readOnly = true)
  public AccountExport export(UUID userId, ContentLocale locale) {
    return new AccountExport(
        clock.instant(),
        identity.getUser(userId),
        identity.linkedProviders(userId),
        addresses.list(userId),
        orders.list(userId, locale),
        favorites.ids(userId),
        notifications.export(userId),
        notifications.pushEnabled(userId),
        markets.ownedMarketIds(userId));
  }

  public void delete(UUID userId, @Nullable String password) {
    deletionAttempts.acquire(userId, "account deletion attempts");
    // First: event handlers writing for this user right now finish before anything is removed; a
    // second deletion request waits here and then fails the password check of the deleted account.
    identity.lockForDeletion(userId);
    identity.confirmAccountOwner(userId, password);
    if (!markets.ownedMarketIds(userId).isEmpty()) {
      throw new ConflictException(
          ErrorCodes.ACCOUNT_OWNS_MARKETS, "This account runs a market. Please contact Dukkan support to close or hand over the market first.");
    }
    if (orders.hasOpenOrders(userId)) {
      throw new ConflictException(ErrorCodes.ACCOUNT_HAS_OPEN_ORDERS, "Please wait until your open orders are completed or cancel them first.");
    }
    erase(userId);
    // Last: a failed write rolls the deletion back, so every erasure the user sees is recorded.
    journal.record(userId, clock.instant());
  }

  /**
   * Erases an account again after a database restore ({@link ErasureReplay}). No password and no
   * checks: the user asked for the deletion before the backup was restored.
   */
  public void reapplyErasure(UUID userId) {
    identity.lockForDeletion(userId);
    erase(userId);
  }

  private void erase(UUID userId) {
    carts.deleteAll(userId);
    favorites.deleteAll(userId);
    addresses.deleteAll(userId);
    notifications.deleteAll(userId);
    orders.anonymizeCustomer(userId);
    identity.anonymize(userId);
  }
}
