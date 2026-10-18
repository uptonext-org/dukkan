package de.dukkan.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A micro-fulfilment hub (seed data, read-only). */
@Entity
@Table(name = "hub")
public class Hub {

  @Id private UUID id;

  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String name;

  protected Hub() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
