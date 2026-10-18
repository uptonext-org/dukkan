package de.dukkan.order.domain;

/** Only PLACED and CANCELLED are produced by the MVP. */
public enum OrderStatus {
  PLACED,
  CANCELLED,
  OUT_FOR_DELIVERY,
  DELIVERED
}
