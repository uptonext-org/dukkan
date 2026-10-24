package de.dukkan.identity.application;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Makes the first platform admin in a fresh installation: the operator registers normally, confirms
 * the address with the link in the welcome mail, sets {@code DUKKAN_BOOTSTRAP_ADMIN_EMAIL} to it and
 * restarts. Does nothing once any admin exists; remove the setting afterwards.
 * Admins then manage everything else (market review, catalog moderation) in the web portal.
 */
@Component
final class AdminBootstrap implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

  private final IdentityService identity;
  private final @Nullable String email;

  AdminBootstrap(IdentityService identity, @Value("${dukkan.security.bootstrap-admin-email:}") @Nullable String email) {
    this.identity = identity;
    this.email = email == null || email.isBlank() ? null : email.trim();
  }

  @Override
  public void run(ApplicationArguments args) {
    if (email == null) {
      return;
    }
    if (identity.anyActiveWithRole("ADMIN")) {
      // Only the very first admin: an address someone else registered later can never become one.
      LOG.warn("Bootstrap admin: an admin exists already; remove DUKKAN_BOOTSTRAP_ADMIN_EMAIL");
      return;
    }
    identity
        .findByEmail(email)
        .ifPresentOrElse(
            user -> {
              if (!user.emailVerified()) {
                // Anyone could have registered the address; only its owner can click the link in the mail.
                LOG.error("Bootstrap admin: the account's e-mail address is not confirmed yet; open the link in the welcome mail, then restart");
                return;
              }
              identity.grantRole(user.id(), "ADMIN");
              LOG.info("Bootstrap admin: user {} has the ADMIN role; remove DUKKAN_BOOTSTRAP_ADMIN_EMAIL", user.id());
            },
            () -> LOG.warn("Bootstrap admin: no account with the configured address yet; register it first"));
  }
}
