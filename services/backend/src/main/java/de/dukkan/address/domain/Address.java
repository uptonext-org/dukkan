package de.dukkan.address.domain;

import static java.util.UUID.randomUUID;

import de.dukkan.address.application.AddressDetails;
import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A delivery address owned by one user. Only German addresses are supported. */
@Entity
@Table(name = "address")
public class Address extends AuditedEntity {

  public static final String COUNTRY_DE = "DE";

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  private String label;

  @Column(nullable = false)
  private String street;

  @Column(name = "house_number", nullable = false)
  private String houseNumber;

  private String addition;

  @Column(name = "postal_code", nullable = false)
  private String postalCode;

  @Column(nullable = false)
  private String city;

  @Column(name = "country_code", nullable = false)
  private String countryCode;

  @Column(name = "delivery_instructions")
  private String deliveryInstructions;

  private Double lat;

  private Double lng;

  @Column(name = "is_default", nullable = false)
  private boolean defaultAddress;

  protected Address() {}

  public Address(UUID userId, AddressDetails details) {
    this.id = randomUUID();
    this.userId = userId;
    this.countryCode = COUNTRY_DE;
    update(details);
  }

  /** Replaces all editable fields. */
  public final void update(AddressDetails details) {
    this.label = details.label();
    this.street = details.street();
    this.houseNumber = details.houseNumber();
    this.addition = details.addition();
    this.postalCode = details.postalCode();
    this.city = details.city();
    this.deliveryInstructions = details.deliveryInstructions();
  }

  /** Sets the coordinates (both null when unknown). */
  public void locate(Double lat, Double lng) {
    this.lat = lat;
    this.lng = lng;
  }

  public Double getLat() {
    return lat;
  }

  public Double getLng() {
    return lng;
  }

  public void setDefaultAddress(boolean defaultAddress) {
    this.defaultAddress = defaultAddress;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getLabel() {
    return label;
  }

  public String getStreet() {
    return street;
  }

  public String getHouseNumber() {
    return houseNumber;
  }

  public String getAddition() {
    return addition;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCity() {
    return city;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public String getDeliveryInstructions() {
    return deliveryInstructions;
  }

  public boolean isDefaultAddress() {
    return defaultAddress;
  }
}
