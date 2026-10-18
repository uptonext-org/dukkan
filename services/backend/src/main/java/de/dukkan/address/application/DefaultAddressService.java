package de.dukkan.address.application;

import static java.util.stream.Collectors.toMap;

import de.dukkan.address.domain.Address;
import de.dukkan.address.domain.AddressDetails;
import de.dukkan.address.domain.DeliveryArea;
import de.dukkan.address.infrastructure.AddressRepository;
import de.dukkan.address.infrastructure.DeliveryAreaRepository;
import de.dukkan.shared.error.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultAddressService implements AddressService, DeliveryAreaService {

  private final AddressRepository addresses;
  private final DeliveryAreaRepository areas;

  DefaultAddressService(AddressRepository addresses, DeliveryAreaRepository areas) {
    this.addresses = addresses;
    this.areas = areas;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AddressView> list(UUID userId) {
    List<Address> owned = addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId);
    Map<String, UUID> hubs =
        areas.findByPostalCodeIn(owned.stream().map(Address::getPostalCode).toList()).stream()
            .collect(toMap(DeliveryArea::getPostalCode, DeliveryArea::getHubId));
    return owned.stream().map(a -> view(a, hubs.get(a.getPostalCode()))).toList();
  }

  @Override
  public AddressView create(UUID userId, AddressDetails details, boolean makeDefault) {
    boolean first = !addresses.existsByUserId(userId);
    if (makeDefault && !first) {
      addresses.clearDefault(userId);
    }
    Address address = new Address(userId, details);
    address.setDefaultAddress(makeDefault || first);
    return view(addresses.save(address));
  }

  @Override
  public AddressView update(UUID userId, UUID addressId, AddressDetails details, boolean makeDefault) {
    if (makeDefault) {
      addresses.clearDefault(userId);
    }
    Address address = owned(userId, addressId);
    address.update(details);
    if (makeDefault) {
      address.setDefaultAddress(true);
    }
    return view(address);
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
    return addresses.findByIdAndUserId(addressId, userId).map(this::view);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findHubId(String postalCode) {
    return areas.findById(postalCode).map(DeliveryArea::getHubId);
  }

  private Address owned(UUID userId, UUID addressId) {
    return addresses
        .findByIdAndUserId(addressId, userId)
        .orElseThrow(() -> new NotFoundException("Address not found."));
  }

  private AddressView view(Address address) {
    return view(address, findHubId(address.getPostalCode()).orElse(null));
  }

  private static AddressView view(Address a, UUID hubId) {
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
        a.isDefaultAddress(),
        hubId);
  }
}
