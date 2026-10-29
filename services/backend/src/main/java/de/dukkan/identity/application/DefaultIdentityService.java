package de.dukkan.identity.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.dukkan.i18n.application.LocaleService;
import de.dukkan.identity.domain.Role;
import de.dukkan.identity.domain.User;
import de.dukkan.identity.infrastructure.SessionRepository;
import de.dukkan.identity.infrastructure.UserRepository;
import de.dukkan.legal.application.TermsPolicy;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.InvalidCredentialsException;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import de.dukkan.shared.error.RateLimitedException;
import de.dukkan.shared.error.UnauthorizedException;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import de.dukkan.shared.security.SecurityEvents;
import java.net.URLEncoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultIdentityService implements IdentityService {

  /** bcrypt input limit; registration enforces it with {@code @MaxUtf8Bytes(72)}. */
  private static final int MAX_PASSWORD_BYTES = 72;

  private static final Logger LOG = LoggerFactory.getLogger(DefaultIdentityService.class);

  private final UserRepository users;
  private final SessionRepository sessions;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokens;
  private final LocaleService locales;
  private final IdTokenVerifier idTokens;
  private final AccountMailer mailer;
  private final TermsPolicy terms;
  private final DukkanProperties.Auth settings;
  private final Clock clock;
  /** Hash compared against when the email is unknown, so both paths cost the same. */
  private final String dummyHash;
  /** Per account, across all client IPs (a botnet trying one account, or mailing one address). */
  private final FixedWindowRateLimiter<String> loginsPerAccount;
  private final FixedWindowRateLimiter<String> loginsPerAccountAndClient;
  private final FixedWindowRateLimiter<UUID> resetMailsPerAccount;
  private final FixedWindowRateLimiter<String> resetMailsPerAccountAndClient;
  private final FixedWindowRateLimiter<UUID> passwordChangeAttempts;
  private final boolean accountLimits;

  DefaultIdentityService(
      UserRepository users,
      SessionRepository sessions,
      PasswordEncoder passwordEncoder,
      JwtTokenService tokens,
      LocaleService locales,
      IdTokenVerifier idTokens,
      AccountMailer mailer,
      TermsPolicy terms,
      DukkanProperties properties,
      Clock clock,
      RateLimitStore rateLimits) {
    this.users = users;
    this.sessions = sessions;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.locales = locales;
    this.idTokens = idTokens;
    this.mailer = mailer;
    this.terms = terms;
    this.settings = properties.security().auth();
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    DukkanProperties.RateLimit limits = properties.security().rateLimit();
    this.accountLimits = limits.enabled();
    // Per account and client: 10 per 15 minutes with the production defaults (the local profile's raised
    // limits raise it too). Per account across all clients ten times that, so guessing from many
    // addresses stays limited while a single client cannot lock the owner out.
    int loginsPerClient = Math.max(10, limits.loginsPerMinute() / 2);
    this.loginsPerAccountAndClient =
        new FixedWindowRateLimiter<>("login-account-client", loginsPerClient, Duration.ofMinutes(15), clock, rateLimits);
    this.loginsPerAccount = new FixedWindowRateLimiter<>("login-account", loginsPerClient * 10, Duration.ofMinutes(15), clock, rateLimits);
    int resetMailsPerClient = Math.max(3, limits.passwordResetsPerHour() / 2);
    this.resetMailsPerAccountAndClient =
        new FixedWindowRateLimiter<>("reset-mail-account-client", resetMailsPerClient, Duration.ofHours(1), clock, rateLimits);
    this.resetMailsPerAccount =
        new FixedWindowRateLimiter<>("reset-mail-account", resetMailsPerClient * 3, Duration.ofHours(1), clock, rateLimits);
    // Like account deletion: the current-password check must not let a stolen session guess the password.
    this.passwordChangeAttempts = new FixedWindowRateLimiter<>("password-change", 5, Duration.ofHours(1), clock, rateLimits);
  }

  @Override
  public AuthResult register(RegisterCommand command) {
    String email = User.normalizeEmail(command.email());
    if (users.existsByEmail(email)) {
      throw new ConflictException(ErrorCodes.EMAIL_TAKEN, "An account with this email already exists.");
    }
    Optional<String> termsVersion = terms.requireAccepted(command.acceptedTermsVersion());
    String locale = validLocale(command.preferredLocale());
    Set<Role> roles = command.merchant() ? EnumSet.of(Role.CUSTOMER, Role.MERCHANT) : EnumSet.of(Role.CUSTOMER);
    User created = new User(email, passwordEncoder.encode(command.password()), command.firstName(), command.lastName(), roles, locale);
    termsVersion.ifPresent(version -> created.acceptTerms(version, clock.instant()));
    User user = users.saveAndFlush(created);
    sendVerification(user);
    return authResult(user);
  }

  @Override
  public AuthResult login(String email, String password, String clientIp) {
    // Counted for every address, existing or not, so the limit reveals nothing about accounts.
    if (accountLimits) {
      String account = User.normalizeEmail(email);
      loginsPerAccountAndClient.acquire(account + "|" + clientIp, "sign-in attempts for this account");
      loginsPerAccount.acquire(account, "sign-in attempts for this account");
    }
    Optional<User> user = users.findByEmail(User.normalizeEmail(email));
    String hash = user.map(User::getPasswordHash).orElse(dummyHash);
    // bcrypt rejects inputs over 72 bytes; no stored password can be that long, so it is simply wrong.
    String candidate = password.getBytes(UTF_8).length > MAX_PASSWORD_BYTES ? "" : password;
    boolean matches = passwordEncoder.matches(candidate, hash) && candidate.equals(password);
    if (user.isEmpty() || !matches) {
      throw new InvalidCredentialsException();
    }
    return authResult(user.orElseThrow());
  }

  /** Commits on INVALID_REFRESH_TOKEN so that the revocation after a reused token is kept. */
  @Override
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public AuthResult refresh(String refreshToken) {
    Instant now = clock.instant();
    SessionRepository.RefreshRow row = sessions.findRefreshForUpdate(OpaqueTokens.hash(refreshToken)).orElseThrow(DefaultIdentityService::invalidRefresh);
    if (row.revokedAt() != null) {
      // A rotated token came back: someone else holds the newer one. End the whole sign-in.
      int revoked = sessions.revokeFamily(row.familyId(), now);
      SecurityEvents.refreshTokenReused(row.userId().toString(), revoked);
      throw invalidRefresh();
    }
    if (!row.expiresAt().isAfter(now)) {
      throw invalidRefresh();
    }
    User user = users.findById(row.userId()).orElseThrow(DefaultIdentityService::invalidRefresh);
    if (user.isDeleted()) {
      // A refresh that raced the deletion may have left a token behind: the account is gone.
      sessions.revokeFamily(row.familyId(), now);
      throw invalidRefresh();
    }
    UserView view = view(user);
    String next = OpaqueTokens.generate();
    UUID nextId = UUID.randomUUID();
    sessions.insertRefresh(nextId, user.getId(), row.familyId(), OpaqueTokens.hash(next), now, now.plus(settings.refreshTokenTtl()));
    sessions.markRotated(row.id(), nextId, now);
    return new AuthResult(tokens.issue(view), tokens.ttl(), next, settings.refreshTokenTtl(), view);
  }

  @Override
  public void logout(String refreshToken) {
    sessions.findRefreshForUpdate(OpaqueTokens.hash(refreshToken)).ifPresent(row -> sessions.revokeFamily(row.familyId(), clock.instant()));
  }

  @Override
  public void requestPasswordReset(String email, String clientIp) {
    Optional<User> found = users.findByEmail(User.normalizeEmail(email));
    if (found.isEmpty()) {
      // Same answer either way (the mail is sent in the background); the caller never learns whether the account exists.
      return;
    }
    User user = found.get();
    try {
      if (accountLimits) {
        resetMailsPerAccountAndClient.acquire(user.getId() + "|" + clientIp, "reset mails");
        resetMailsPerAccount.acquire(user.getId(), "reset mails");
      }
    } catch (RateLimitedException e) {
      // Same answer as always; the address just gets no further mail this hour.
      LOG.info("Password reset mail for user {} suppressed (limit per hour reached)", user.getId());
      return;
    }
    Instant now = clock.instant();
    String token = OpaqueTokens.generate();
    sessions.insertReset(UUID.randomUUID(), user.getId(), OpaqueTokens.hash(token), now, now.plus(settings.passwordResetTtl()));
    String separator = settings.passwordResetUrl().contains("?") ? "&" : "?";
    String link = settings.passwordResetUrl() + separator + "token=" + URLEncoder.encode(token, UTF_8);
    mailer.sendPasswordReset(user.getEmail(), user.getFirstName(), user.getPreferredLocale(), link);
  }

  @Override
  public void resetPassword(String token, String newPassword) {
    Instant now = clock.instant();
    SessionRepository.ResetRow row =
        sessions.findResetForUpdate(OpaqueTokens.hash(token)).filter(r -> r.usedAt() == null && r.expiresAt().isAfter(now)).orElseThrow(
            () -> new InvalidRequestException("token", ErrorCodes.INVALID_RESET_TOKEN, "The reset link is invalid, used or expired."));
    User user = user(row.userId());
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    sessions.useAllResets(user.getId(), now);
    sessions.revokeAllForUser(user.getId(), now);
  }

  @Override
  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    if (accountLimits) {
      passwordChangeAttempts.acquire(userId, "password changes");
    }
    User user = user(userId);
    String candidate = currentPassword.getBytes(UTF_8).length > MAX_PASSWORD_BYTES ? "" : currentPassword;
    if (!passwordEncoder.matches(candidate, user.getPasswordHash()) || !candidate.equals(currentPassword)) {
      throw new InvalidRequestException("currentPassword", "Incorrect", "The current password is wrong.");
    }
    Instant now = clock.instant();
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    sessions.useAllResets(userId, now);
    sessions.revokeAllForUser(userId, now);
  }

  @Override
  public AuthResult socialSignIn(SocialSignInCommand command) {
    if (!idTokens.enabled(command.provider())) {
      throw new BusinessRuleException(ErrorCodes.SOCIAL_PROVIDER_DISABLED, command.provider() + " sign-in is not available.");
    }
    IdTokenVerifier.VerifiedIdToken token = idTokens.verify(command.provider(), command.idToken(), command.nonce());
    String provider = command.provider().name();
    Instant now = clock.instant();
    Optional<User> linked = sessions.findIdentity(provider, token.subject()).flatMap(users::findById);
    if (linked.isPresent()) {
      return authResult(linked.get());
    }
    if (token.email() == null || !token.emailVerified()) {
      throw new BusinessRuleException(ErrorCodes.SOCIAL_EMAIL_MISSING, "The provider did not confirm an email address.");
    }
    String email = User.normalizeEmail(token.email());
    User user =
        users
            .findByEmail(email)
            .orElseGet(
                () -> {
                  Optional<String> termsVersion = terms.requireAccepted(command.acceptedTermsVersion());
                  Set<Role> roles = command.merchant() ? EnumSet.of(Role.CUSTOMER, Role.MERCHANT) : EnumSet.of(Role.CUSTOMER);
                  String first = firstNonBlank(command.firstName(), token.givenName(), email.substring(0, email.indexOf('@')));
                  String last = firstNonBlank(command.lastName(), token.familyName(), "");
                  // No password: a random one nobody knows; "forgot password" can set a real one later.
                  String unusable = passwordEncoder.encode(OpaqueTokens.generate());
                  User created = new User(email, unusable, clip(first), clip(last), roles, validLocale(command.preferredLocale()));
                  created.replaceWithUnknownPassword(unusable);
                  termsVersion.ifPresent(version -> created.acceptTerms(version, now));
                  return users.saveAndFlush(created);
                });
    if (!user.isEmailVerified()) {
      // Someone registered this address with a password but never confirmed it. The provider has now
      // proven who owns the address: end that password and every session of it, so an account
      // created in advance by someone else cannot be taken over this way (pre-hijacking).
      user.replaceWithUnknownPassword(passwordEncoder.encode(OpaqueTokens.generate()));
      sessions.revokeAllForUser(user.getId(), now);
    }
    sessions.insertIdentity(provider, token.subject(), user.getId(), email, now);
    // The provider confirmed this email address (checked above).
    user.markEmailVerified(now);
    return authResult(user);
  }

  private static String firstNonBlank(@Nullable String a, @Nullable String b, String fallback) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    return b != null && !b.isBlank() ? b : fallback;
  }

  private static String clip(String name) {
    String stripped = name.strip();
    return stripped.length() <= 100 ? stripped : stripped.substring(0, 100);
  }

  private static UnauthorizedException invalidRefresh() {
    return new UnauthorizedException(ErrorCodes.INVALID_REFRESH_TOKEN, "The session has ended. Please sign in again.");
  }

  @Override
  @Transactional(readOnly = true)
  public UserView getUser(UUID userId) {
    return view(user(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserView> findByEmail(String email) {
    return users.findByEmail(User.normalizeEmail(email)).map(DefaultIdentityService::view);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean anyActiveWithRole(String role) {
    return users.countActiveWithRole(role) > 0;
  }

  @Override
  public UserView grantRole(UUID userId, String role) {
    User user = user(userId);
    user.grantRole(Role.valueOf(role));
    SecurityEvents.roleGranted(userId.toString(), role);
    return view(user);
  }

  @Override
  @Transactional(readOnly = true)
  public void confirmAccountOwner(UUID userId, @Nullable String password) {
    User user = user(userId);
    // Only accounts that never had a password of their own (Apple/Google sign-up) may omit it; a
    // linked provider alone is not enough: a stolen session must not delete a password account.
    if (password == null && !user.hasOwnPassword() && !sessions.identityProviders(userId).isEmpty()) {
      return;
    }
    String candidate = password == null || password.getBytes(UTF_8).length > MAX_PASSWORD_BYTES ? "" : password;
    if (!passwordEncoder.matches(candidate, user.getPasswordHash()) || !candidate.equals(password)) {
      throw new InvalidRequestException("password", "Incorrect", "The password is wrong.");
    }
  }

  @Override
  public void verifyEmail(String token) {
    Instant now = clock.instant();
    SessionRepository.ResetRow row =
        sessions
            .findVerificationForUpdate(OpaqueTokens.hash(token))
            .filter(r -> r.usedAt() == null && r.expiresAt().isAfter(now))
            .orElseThrow(() -> new InvalidRequestException("token", ErrorCodes.INVALID_VERIFICATION_TOKEN, "The link is invalid, used or expired."));
    user(row.userId()).markEmailVerified(now);
    sessions.useAllVerifications(row.userId(), now);
  }

  @Override
  public void resendVerification(UUID userId) {
    User user = user(userId);
    if (!user.isEmailVerified() && !user.isDeleted()) {
      sendVerification(user);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isEmailVerified(UUID userId) {
    return users.findById(userId).map(User::isEmailVerified).orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireVerifiedEmail(UUID userId) {
    if (settings.requireVerifiedEmail() && !isEmailVerified(userId)) {
      throw new BusinessRuleException(ErrorCodes.EMAIL_NOT_VERIFIED, "Please confirm your email address first (link in the welcome email).");
    }
  }

  private void sendVerification(User user) {
    Instant now = clock.instant();
    String token = OpaqueTokens.generate();
    sessions.insertVerification(UUID.randomUUID(), user.getId(), OpaqueTokens.hash(token), now, now.plus(settings.emailVerificationTtl()));
    String separator = settings.emailVerificationUrl().contains("?") ? "&" : "?";
    String link = settings.emailVerificationUrl() + separator + "token=" + URLEncoder.encode(token, UTF_8);
    mailer.sendEmailVerification(user.getEmail(), user.getFirstName(), user.getPreferredLocale(), link);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isDeleted(UUID userId) {
    return users.findById(userId).map(User::isDeleted).orElse(true);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean holdActiveAccount(UUID userId) {
    return users.lockActive(userId).orElse(false);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForDeletion(UUID userId) {
    users.lockForUpdate(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> linkedProviders(UUID userId) {
    return sessions.identityProviders(userId);
  }

  @Override
  public void anonymize(UUID userId) {
    User user = user(userId);
    user.anonymize("deleted-" + userId + "@deleted.invalid", passwordEncoder.encode(OpaqueTokens.generate()), clock.instant());
    sessions.deleteAllForUser(userId);
    SecurityEvents.accountDeleted(userId.toString());
  }

  @Override
  public UserView updateProfile(
      UUID userId, @Nullable String firstName, @Nullable String lastName, @Nullable String preferredLocale) {
    User user = user(userId);
    user.updateProfile(firstName, lastName, validLocale(preferredLocale));
    return view(user);
  }

  private @Nullable String validLocale(@Nullable String locale) {
    if (locale != null && !locales.isEnabled(locale)) {
      throw new InvalidRequestException("preferredLocale", "SupportedLocale", "must be one of the enabled locales");
    }
    return locale;
  }

  private User user(UUID userId) {
    return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
  }

  /** Issues an access token and starts a new sign-in (refresh token family). */
  private AuthResult authResult(User user) {
    UserView view = view(user);
    Instant now = clock.instant();
    String refresh = OpaqueTokens.generate();
    sessions.insertRefresh(UUID.randomUUID(), user.getId(), UUID.randomUUID(), OpaqueTokens.hash(refresh), now, now.plus(settings.refreshTokenTtl()));
    return new AuthResult(tokens.issue(view), tokens.ttl(), refresh, settings.refreshTokenTtl(), view);
  }

  private static UserView view(User user) {
    return new UserView(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRoles().stream().map(Role::name).sorted().toList(),
        user.getPreferredLocale(),
        user.isEmailVerified());
  }
}
