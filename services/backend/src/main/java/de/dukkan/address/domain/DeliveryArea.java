package de.dukkan.address.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A served postal code and the hub that serves it (seed data, read-only). */
@Entity
@Table(name = "delivery_area")
public class DeliveryArea {

  @Column(name = "postal_code")
  @Id
  private String postalCode;

  @Column(name = "hub_id", nullable = false)
  private UUID hubId;

  protected DeliveryArea() {}

  public String getPostalCode() {
    return postalCode;
  }

  public UUID getHubId() {
    return hubId;
  }
}
