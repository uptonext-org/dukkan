package de.dukkan.address.application;

import java.util.Optional;
import java.util.UUID;

/** Serviceability lookup: which hub (if any) serves a postal code. */
public interface DeliveryAreaService {

  Optional<UUID> findHubId(String postalCode);
}
