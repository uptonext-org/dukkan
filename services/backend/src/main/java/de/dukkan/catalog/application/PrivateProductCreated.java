package de.dukkan.catalog.application;

import java.util.UUID;

/** Payload of the {@code catalog.private_product_created} event (outbox). */
public record PrivateProductCreated(UUID catalogProductId, UUID marketId) {

  public static final String TYPE = "catalog.private_product_created";
}
