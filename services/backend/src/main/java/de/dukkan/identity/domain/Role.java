package de.dukkan.identity.domain;

/** Account roles, carried in the JWT {@code roles} claim. */
public enum Role {
  CUSTOMER,
  MERCHANT,
  ADMIN
}
