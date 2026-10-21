package de.dukkan.address.application;

import de.dukkan.address.domain.Address;
import de.dukkan.address.infrastructure.AddressRepository;
import de.dukkan.geo.application.GeocodingService;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultAddressService implements AddressService {

  private final AddressRepository addresses;
  private final GeocodingService geocoding;

  DefaultAddressService(AddressRepository addresses, GeocodingService geocoding) {
    this.addresses = addresses;
    this.geocoding = geocoding;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AddressView> list(UUID userId) {
    return addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId).stream()
        .map(DefaultAddressService::view)
        .toList();
  }

  @Override
  public AddressView create(UUID userId, AddressDetails details, @Nullable GeoPoint location, boolean makeDefault) {
    boolean first = !addresses.existsByUserId(userId);
    if (makeDefault && !first) {
      addresses.clearDefault(userId);
    }
    Address address = new Address(userId, details);
    locate(address, details, location);
    address.setDefaultAddress(makeDefault || first);
    return view(addresses.save(address));
  }

  @Override
  public AddressView update(
      UUID userId, UUID addressId, AddressDetails details, @Nullable GeoPoint location, boolean makeDefault) {
    if (makeDefault) {
      addresses.clearDefault(userId);
    }
    Address address = owned(userId, addressId);
    address.update(details);
    locate(address, details, location);
    if (makeDefault) {
      address.setDefaultAddress(true);
    }
    return view(address);
  }

  private void locate(Address address, AddressDetails details, @Nullable GeoPoint location) {
    GeoPoint point =
        location != null
            ? location
            : geocoding.geocode(details.street(), details.postalCode(), details.city()).orElse(null);
    address.locate(point == null ? null : point.lat(), point == null ? null : point.lng());
  }

  @Override
  public void delete(UUID userId, UUID addressId) {
    addresses.delete(owned(userId, addressId));
  }

  @Override
  @Transactional(readOnly = true)
  public AddressView getOwned(UUID userId, UUID addressId) {
    return view(owned(userId, addressId));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AddressView> findOwned(UUID userId, UUID addressId) {
    return addresses.findByIdAndUserId(addressId, userId).map(DefaultAddressService::view);
  }

  private Address owned(UUID userId, UUID addressId) {
    return addresses.findByIdAndUserId(addressId, userId).orElseThrow(() -> new NotFoundException("Address not found."));
  }

  private static AddressView view(Address a) {
    return new AddressView(
        a.getId(),
        a.getLabel(),
        a.getStreet(),
        a.getHouseNumber(),
        a.getAddition(),
        a.getPostalCode(),
        a.getCity(),
        a.getCountryCode(),
        a.getDeliveryInstructions(),
        a.getLat() == null || a.getLng() == null ? null : GeoPoint.of(a.getLat(), a.getLng()),
        a.isDefaultAddress());
  }
}
