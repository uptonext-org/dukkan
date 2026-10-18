package de.dukkan.delivery.application;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.address.application.DeliveryAreaService;
import de.dukkan.delivery.domain.DeliverySlot;
import de.dukkan.delivery.domain.SlotPlanner;
import de.dukkan.delivery.infrastructure.DeliverySlotRepository;
import de.dukkan.delivery.infrastructure.HubRepository;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultDeliveryService implements DeliveryService {

  private final DeliverySlotRepository slots;
  private final HubRepository hubs;
  private final AddressService addresses;
  private final DeliveryAreaService areas;
  private final Clock clock;
  private final SlotPlanner planner;
  private final int slotCapacity;

  DefaultDeliveryService(
      DeliverySlotRepository slots,
      HubRepository hubs,
      AddressService addresses,
      DeliveryAreaService areas,
      Clock clock,
      DukkanProperties properties) {
    this.slots = slots;
    this.hubs = hubs;
    this.addresses = addresses;
    this.areas = areas;
    this.clock = clock;
    DukkanProperties.Delivery delivery = properties.delivery();
    this.planner =
        new SlotPlanner(
            delivery.daysAhead(), delivery.firstSlotHour(), delivery.lastSlotHour(), delivery.cutoffHour());
    this.slotCapacity = delivery.slotCapacity();
  }

  @Override
  public List<SlotView> listSlotsForAddress(UUID userId, UUID addressId) {
    AddressView address = addresses.getOwned(userId, addressId);
    UUID hubId = address.hubId();
    if (hubId == null) {
      throw new BusinessRuleException(ErrorCodes.ADDRESS_NOT_SERVICEABLE, "We do not deliver to this address yet.");
    }
    Instant now = clock.instant();
    List<DeliverySlot> existing = slotsInHorizon(hubId, now);
    if (ensureSlots(hubId, now, existing)) {
      existing = slotsInHorizon(hubId, now);
    }
    return existing.stream().map(slot -> view(slot, now)).toList();
  }

  private List<DeliverySlot> slotsInHorizon(UUID hubId, Instant now) {
    return slots.findByHubIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
        hubId, now, planner.horizonEnd(now));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<HubView> findHubForPostalCode(String postalCode) {
    return areas.findHubId(postalCode).flatMap(hubs::findById).map(h -> new HubView(h.getId(), h.getName()));
  }

  @Override
  @Transactional(readOnly = true)
  public SlotView getSlot(UUID slotId) {
    return slots.findById(slotId).map(s -> view(s, clock.instant())).orElseThrow(DefaultDeliveryService::slotNotFound);
  }

  @Override
  public SlotView reserveCapacity(UUID slotId, UUID hubId) {
    DeliverySlot slot = slots.findByIdForUpdate(slotId).orElseThrow(DefaultDeliveryService::slotNotFound);
    if (!slot.getHubId().equals(hubId)) {
      throw slotNotFound();
    }
    Instant now = clock.instant();
    if (slot.isCutoffPassed(now)) {
      throw new BusinessRuleException(ErrorCodes.SLOT_CUTOFF_PASSED, "The order cutoff for this slot has passed.");
    }
    if (slot.remainingCapacity() <= 0) {
      throw new ConflictException(ErrorCodes.SLOT_FULL, "This delivery slot is fully booked.");
    }
    slot.consumeOne();
    return view(slot, now);
  }

  @Override
  public void releaseCapacity(UUID slotId) {
    slots.findByIdForUpdate(slotId).ifPresent(DeliverySlot::releaseOne);
  }

  /** Creates the planned slots that do not exist yet; returns whether any were inserted. */
  private boolean ensureSlots(UUID hubId, Instant now, List<DeliverySlot> existing) {
    Set<Instant> existingStarts = existing.stream().map(DeliverySlot::getStartsAt).collect(toSet());
    boolean inserted = false;
    for (SlotPlanner.PlannedSlot planned : planner.plan(now)) {
      if (existingStarts.contains(planned.startsAt())) {
        continue;
      }
      inserted = true;
      slots.insertIfAbsent(
          randomUUID(),
          hubId,
          planned.startsAt(),
          planned.endsAt(),
          planned.orderCutoffAt(),
          planned.feeCents(),
          slotCapacity);
    }
    return inserted;
  }

  private static SlotView view(DeliverySlot slot, Instant now) {
    int remaining = slot.remainingCapacity();
    return new SlotView(
        slot.getId(),
        slot.getHubId(),
        slot.getStartsAt(),
        slot.getEndsAt(),
        slot.getOrderCutoffAt(),
        slot.getFeeCents(),
        remaining,
        remaining > 0 && !slot.isCutoffPassed(now));
  }

  private static NotFoundException slotNotFound() {
    return new NotFoundException("Delivery slot not found.");
  }
}
