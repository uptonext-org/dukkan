package de.dukkan.identity.application;

import java.util.UUID;

/** Read model of a user, exposed to other modules and the API layer. */
public record UserView(UUID id, String email, String firstName, String lastName) {}
