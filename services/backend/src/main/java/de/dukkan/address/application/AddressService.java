package de.dukkan.address.application;

import de.dukkan.shared.api.GeoPoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Manages a user's addresses. Every lookup is scoped to the owning user. */
public interface AddressService {

  /** The user's addresses, default first. */
  List<AddressView> list(UUID userId);

  /** Without {@code location} the address is geocoded (stored without coordinates if that fails). */
  AddressView create(UUID userId, AddressDetails details, @Nullable GeoPoint location, boolean makeDefault);

  AddressView update(UUID userId, UUID addressId, AddressDetails details, @Nullable GeoPoint location, boolean makeDefault);

  void delete(UUID userId, UUID addressId);

  /** Returns the address if it exists and belongs to the user; otherwise {@code NOT_FOUND}. */
  AddressView getOwned(UUID userId, UUID addressId);

  Optional<AddressView> findOwned(UUID userId, UUID addressId);
}
