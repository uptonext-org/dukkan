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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
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
  private final SlotPlanner planner;
  private final DukkanProperties.Fulfilment settings;

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
    this.settings = properties.fulfilment();
    this.planner = new SlotPlanner(settings.daysAhead(), settings.slotLength());
  }

  /**
   * Slots of an active market for the next days, ordered by start. With {@code addressId}
   * (DELIVERY only) the caller must be signed in, own the address and be inside the radius.
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
    ensureSlots(market, type, now);
    return slots
        .findByMarketIdAndTypeAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
            marketId, type, now, planner.horizonEnd(now))
        .stream()
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
    if (slot.getType() != type) {
      throw new BusinessRuleException(ErrorCodes.SLOT_TYPE_MISMATCH, "The slot is not a " + type + " slot.");
    }
    if (slot.isCutoffPassed(now)) {
      throw new BusinessRuleException(ErrorCodes.SLOT_CUTOFF_PASSED, "The order cutoff for this slot has passed.");
    }
  }

  /** Inserts planned slots that do not exist yet (idempotent under concurrency). */
  private void ensureSlots(MarketInfo market, FulfilmentType type, Instant now) {
    Duration cutoff = type == FulfilmentType.DELIVERY ? settings.deliveryCutoff() : settings.pickupCutoff();
    int capacity = type == FulfilmentType.DELIVERY ? settings.deliveryCapacity() : settings.pickupCapacity();
    List<SlotPlanner.OpeningWindow> hours =
        market.openingHours().stream()
            .map(h -> new SlotPlanner.OpeningWindow(DayOfWeek.of(h.dayOfWeek()), h.opensAt(), h.closesAt()))
            .toList();
    List<SlotPlanner.PlannedSlot> planned = planner.plan(now, hours, cutoff);
    if (planned.isEmpty()) {
      return;
    }
    Set<Instant> existing =
        slots
            .findByMarketIdAndTypeAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                market.id(), type, now, planner.horizonEnd(now))
            .stream()
            .map(FulfilmentSlot::getStartsAt)
            .collect(toSet());
    for (SlotPlanner.PlannedSlot slot : planned) {
      if (!existing.contains(slot.startsAt())) {
        slots.insertIfAbsent(
            randomUUID(), market.id(), type.name(), slot.startsAt(), slot.endsAt(), slot.orderCutoffAt(), capacity);
      }
    }
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
