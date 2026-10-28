package de.dukkan.shared.security;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security-relevant events in one log category, {@code de.dukkan.security}, so that the operator can
 * filter and alert on them (docs/launch/anforderungen.md, point 16). Each line carries the event
 * name and only what is needed to follow it up: account ids (never e-mail addresses), the client
 * IP for attacks from outside, paths. Passwords, tokens, request bodies and personal data are never
 * logged. The logs rotate with the container (compose.yaml: 5 × 20 MB).
 */
public final class SecurityEvents {

  private static final Logger LOG = LoggerFactory.getLogger("de.dukkan.security");

  private SecurityEvents() {}

  /** A sign-in with an unknown address or a wrong password. */
  public static void loginFailed(String clientIp) {
    LOG.atWarn().addKeyValue("event", "login_failed").addKeyValue("client_ip", clientIp).log("security: login failed");
  }

  /** A request refused by a rate limit (sign-in, registration, password reset, uploads, …). */
  public static void rateLimited(String limit, String clientIp, String path) {
    LOG.atWarn()
        .addKeyValue("event", "rate_limited")
        .addKeyValue("limit", limit)
        .addKeyValue("client_ip", clientIp)
        .addKeyValue("path", path)
        .log("security: rate limit {} reached", limit);
  }

  /** A signed-in caller without the role for a path (403). */
  public static void accessDenied(@Nullable String accountId, String clientIp, String method, String path) {
    LOG.atWarn()
        .addKeyValue("event", "access_denied")
        .addKeyValue("account", accountId == null ? "anonymous" : accountId)
        .addKeyValue("client_ip", clientIp)
        .addKeyValue("path", method + " " + path)
        .log("security: access denied");
  }

  /** A refresh token used twice: the sign-in is revoked (a stolen token, or a client bug). */
  public static void refreshTokenReused(String accountId, int revoked) {
    LOG.atWarn()
        .addKeyValue("event", "refresh_token_reused")
        .addKeyValue("account", accountId)
        .addKeyValue("revoked", revoked)
        .log("security: refresh token reused; {} token(s) revoked", revoked);
  }

  /** A role given to an account. */
  public static void roleGranted(String accountId, String role) {
    LOG.atInfo()
        .addKeyValue("event", "role_granted")
        .addKeyValue("account", accountId)
        .addKeyValue("role", role)
        .log("security: role {} granted", role);
  }

  /** An account deleted at its owner's request (or erased again after a restore). */
  public static void accountDeleted(String accountId) {
    LOG.atInfo().addKeyValue("event", "account_deleted").addKeyValue("account", accountId).log("security: account deleted");
  }

  /** An admin changed platform state: market review, features, catalog. */
  public static void adminAction(String adminId, String action, String target) {
    LOG.atInfo()
        .addKeyValue("event", "admin_action")
        .addKeyValue("admin", adminId)
        .addKeyValue("action", action)
        .addKeyValue("target", target)
        .log("security: admin {} on {}", action, target);
  }
}
