package de.dukkan.fulfilment.application;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.fulfilment.domain.FulfilmentSlot;
import de.dukkan.fulfilment.domain.SlotPlanner;
import de.dukkan.fulfilment.infrastructure.FulfilmentSlotRepository;
import de.dukkan.geo.application.GeoMath;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.NotFoundException;
import de.dukkan.shared.error.UnauthorizedException;
import de.dukkan.shared.time.DukkanTime;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Delivery and pickup slots: on-demand generation, capacity under row locks, delivery area check. */
@Service
@Transactional
public class FulfilmentService {

  private final FulfilmentSlotRepository slots;
  private final MarketService markets;
  private final AddressService addresses;
  private final Clock clock;
  private final Duration slotLength;

  public FulfilmentService(
      FulfilmentSlotRepository slots,
      MarketService markets,
      AddressService addresses,
      Clock clock,
      DukkanProperties properties) {
    this.slots = slots;
    this.markets = markets;
    this.addresses = addresses;
    this.clock = clock;
    this.slotLength = properties.fulfilment().slotLength();
  }

  /**
   * Slots of an active market for the next days, ordered by start. With {@code addressId}
   * (DELIVERY only) the caller must be signed in, own the address and be inside the radius. A
   * market that takes no orders right now (switched off or paused) has no slots; holidays and
   * times outside the delivery or opening hours have none either.
   */
  public List<SlotView> listSlots(
      UUID marketId, FulfilmentType type, @Nullable UUID userId, @Nullable UUID addressId) {
    MarketInfo market = markets.activeInfo(marketId);
    requireOffered(market, type);
    if (addressId != null && type == FulfilmentType.DELIVERY) {
      if (userId == null) {
        throw new UnauthorizedException("Sign in to check slots for an address.");
      }
      checkDeliveryAddress(market, addresses.getOwned(userId, addressId));
    }
    Instant now = clock.instant();
    if (!market.acceptsOrders(now)) {
      return List.of();
    }
    Set<Instant> planned = ensureSlots(market, type, now);
    SlotPlanner planner = planner(market);
    return slots
        .findByMarketIdAndTypeAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
            marketId, type, now, planner.horizonEnd(now))
        .stream()
        // Booked slots the merchant no longer offers (holiday, new hours) stay for their orders only.
        .filter(slot -> planned.contains(slot.getStartsAt()))
        .map(slot -> view(slot, market, now))
        .toList();
  }

  /** Several slots in one query (plus one per distinct market); unknown ids are absent. */
  @Transactional(readOnly = true)
  public Map<UUID, SlotView> getSlots(Collection<UUID> slotIds) {
    if (slotIds.isEmpty()) {
      return Map.of();
    }
    List<FulfilmentSlot> found = slots.findAllById(new HashSet<>(slotIds));
    Map<UUID, MarketInfo> marketInfos = markets.infos(found.stream().map(FulfilmentSlot::getMarketId).toList());
    Instant now = clock.instant();
    Map<UUID, SlotView> result = new HashMap<>();
    for (FulfilmentSlot slot : found) {
      MarketInfo market = marketInfos.get(slot.getMarketId());
      if (market != null) {
        result.put(slot.getId(), view(slot, market, now));
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  public SlotView getSlot(UUID slotId) {
    FulfilmentSlot slot = slots.findById(slotId).orElseThrow(FulfilmentService::slotNotFound);
    return view(slot, markets.info(slot.getMarketId()), clock.instant());
  }

  /**
   * Takes one unit of capacity under a row lock. Fails with {@code NOT_FOUND} (other market),
   * {@code SLOT_TYPE_MISMATCH}, {@code SLOT_CUTOFF_PASSED} or {@code SLOT_FULL}.
   */
  public SlotView reserveCapacity(UUID slotId, MarketInfo market, FulfilmentType type) {
    FulfilmentSlot slot = slots.findByIdForUpdate(slotId).orElseThrow(FulfilmentService::slotNotFound);
    Instant now = clock.instant();
    checkUsable(slot, market, type, now);
    if (slot.remainingCapacity() <= 0) {
      throw new ConflictException(ErrorCodes.SLOT_FULL, "This slot is fully booked.");
    }
    slot.consumeOne();
    return view(slot, market, now);
  }

  /** Validates a slot that is already held (same slot re-reserved) without taking capacity. */
  @Transactional(readOnly = true)
  public SlotView checkHeldSlot(UUID slotId, MarketInfo market, FulfilmentType type) {
    FulfilmentSlot slot = slots.findById(slotId).orElseThrow(FulfilmentService::slotNotFound);
    Instant now = clock.instant();
    checkUsable(slot, market, type, now);
    return view(slot, market, now);
  }

  /** Returns one unit of capacity (cart cleared, reservation replaced, order cancelled or rejected). */
  public void releaseCapacity(UUID slotId) {
    slots.findByIdForUpdate(slotId).ifPresent(FulfilmentSlot::releaseOne);
  }

  /** Fails with {@code ORDERS_NOT_ACCEPTED} while the market takes no orders (switched off or paused). */
  public static void requireAcceptsOrders(MarketInfo market, Instant now) {
    if (!market.acceptsOrders(now)) {
      throw new BusinessRuleException(ErrorCodes.ORDERS_NOT_ACCEPTED, "This market is currently not taking orders.");
    }
  }

  /** Fails with {@code FULFILMENT_NOT_OFFERED} if the market does not offer the type. */
  public void requireOffered(MarketInfo market, FulfilmentType type) {
    if (!market.offers(type)) {
      throw new BusinessRuleException(
          ErrorCodes.FULFILMENT_NOT_OFFERED, "This market does not offer " + type.name().toLowerCase(java.util.Locale.ROOT) + ".");
    }
  }

  /** Fails with {@code ADDRESS_OUTSIDE_DELIVERY_AREA} unless the address lies in the delivery radius. */
  public void checkDeliveryAddress(MarketInfo market, AddressView address) {
    Double radiusKm = market.deliveryRadiusKm();
    if (address.location() == null || radiusKm == null || GeoMath.distanceMeters(market.location(), address.location()) > radiusKm * 1000) {
      throw new BusinessRuleException(
          ErrorCodes.ADDRESS_OUTSIDE_DELIVERY_AREA, "The address is outside the market's delivery area.");
    }
  }

  private void checkUsable(FulfilmentSlot slot, MarketInfo market, FulfilmentType type, Instant now) {
    if (!slot.getMarketId().equals(market.id())) {
      throw slotNotFound();
    }
    requireAcceptsOrders(market, now);
    if (market.ordering().closedOn(LocalDate.ofInstant(slot.getStartsAt(), DukkanTime.ZONE))) {
      throw new BusinessRuleException(ErrorCodes.SLOT_UNAVAILABLE, "The market is closed on this day.");
    }
    if (slot.getType() != type) {
      throw new BusinessRuleException(ErrorCodes.SLOT_TYPE_MISMATCH, "The slot is not a " + type + " slot.");
    }
    if (slot.isCutoffPassed(now)) {
      throw new BusinessRuleException(ErrorCodes.SLOT_CUTOFF_PASSED, "The order cutoff for this slot has passed.");
    }
  }

  private SlotPlanner planner(MarketInfo market) {
    return new SlotPlanner(market.ordering().daysAhead(), slotLength);
  }

  /**
   * Brings the stored slots in line with the market's settings: inserts planned slots that do not
   * exist yet (idempotent under concurrency), applies a changed capacity or lead time to existing
   * ones (never below what is booked) and deletes unbooked slots that are no longer planned.
   * Returns the start times of the planned slots.
   */
  private Set<Instant> ensureSlots(MarketInfo market, FulfilmentType type, Instant now) {
    MarketInfo.Ordering ordering = market.ordering();
    Duration cutoff = Duration.ofMinutes(type == FulfilmentType.DELIVERY ? ordering.deliveryLeadMinutes() : ordering.pickupLeadMinutes());
    int capacity = type == FulfilmentType.DELIVERY ? ordering.deliveryCapacity() : ordering.pickupCapacity();
    List<SlotPlanner.OpeningWindow> hours =
        (type == FulfilmentType.DELIVERY ? ordering.deliveryHours() : market.openingHours()).stream()
            .map(h -> new SlotPlanner.OpeningWindow(DayOfWeek.of(h.dayOfWeek()), h.opensAt(), h.closesAt()))
            .toList();
    SlotPlanner planner = planner(market);
    Map<Instant, SlotPlanner.PlannedSlot> planned = new HashMap<>();
    for (SlotPlanner.PlannedSlot slot : planner.plan(now, hours, cutoff)) {
      if (!ordering.closedOn(LocalDate.ofInstant(slot.startsAt(), DukkanTime.ZONE))) {
        planned.put(slot.startsAt(), slot);
      }
    }
    // The longest possible horizon, so that slots of a shortened one are tidied up too.
    Instant horizon = new SlotPlanner(SlotPlanner.MAX_DAYS_AHEAD, slotLength).horizonEnd(now);
    Set<Instant> existing = new HashSet<>();
    for (FulfilmentSlot slot :
        slots.findByMarketIdAndTypeAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(market.id(), type, now, horizon)) {
      SlotPlanner.PlannedSlot plan = planned.get(slot.getStartsAt());
      if (plan == null || !plan.endsAt().equals(slot.getEndsAt())) {
        slots.deleteIfUnused(slot.getId());
      } else {
        existing.add(slot.getStartsAt());
        if (slot.getCapacity() != Math.max(capacity, slot.getUsedCapacity()) || !slot.getOrderCutoffAt().equals(plan.orderCutoffAt())) {
          slots.updateRules(slot.getId(), capacity, plan.orderCutoffAt());
        }
      }
    }
    for (SlotPlanner.PlannedSlot slot : planned.values()) {
      if (!existing.contains(slot.startsAt())) {
        slots.insertIfAbsent(
            randomUUID(), market.id(), type.name(), slot.startsAt(), slot.endsAt(), slot.orderCutoffAt(), capacity);
      }
    }
    return planned.keySet();
  }

  private static SlotView view(FulfilmentSlot slot, MarketInfo market, Instant now) {
    int remaining = slot.remainingCapacity();
    long fee = slot.getType() == FulfilmentType.DELIVERY ? market.deliveryFeeCents() : 0;
    return new SlotView(
        slot.getId(),
        slot.getMarketId(),
        slot.getType(),
        slot.getStartsAt(),
        slot.getEndsAt(),
        slot.getOrderCutoffAt(),
        fee,
        remaining,
        remaining > 0 && !slot.isCutoffPassed(now));
  }

  private static NotFoundException slotNotFound() {
    return new NotFoundException("Slot not found.");
  }
}
