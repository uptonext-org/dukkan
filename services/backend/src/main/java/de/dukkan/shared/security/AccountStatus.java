package de.dukkan.shared.security;

import java.util.UUID;

/**
 * Port to the identity module: whether an account can no longer act. Access tokens are
 * self-contained and stay valid until they expire, so a deleted account would otherwise keep
 * working for up to the token lifetime.
 */
public interface AccountStatus {

  /** True for deleted and unknown accounts. */
  boolean isClosed(UUID userId);
}
