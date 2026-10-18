package de.dukkan.delivery.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Delivery slots and their capacity. */
public interface DeliveryService {

  /** Slots of the hub serving the user's address (generated on demand), ordered by start time. */
  List<SlotView> listSlotsForAddress(UUID userId, UUID addressId);

  Optional<HubView> findHubForPostalCode(String postalCode);

  SlotView getSlot(UUID slotId);

  /**
   * Takes one unit of capacity on the slot under a row lock. Fails with {@code NOT_FOUND} if the
   * slot does not belong to {@code hubId}, {@code SLOT_CUTOFF_PASSED} or {@code SLOT_FULL}.
   */
  SlotView reserveCapacity(UUID slotId, UUID hubId);

  /** Returns one unit of capacity (cart cleared, reservation replaced, order cancelled). */
  void releaseCapacity(UUID slotId);
}
