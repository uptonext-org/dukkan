package de.dukkan.identity.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.dukkan.i18n.application.LocaleService;
import de.dukkan.identity.domain.Role;
import de.dukkan.identity.domain.User;
import de.dukkan.identity.infrastructure.UserRepository;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.InvalidCredentialsException;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultIdentityService implements IdentityService {

  /** bcrypt input limit; registration enforces it with {@code @MaxUtf8Bytes(72)}. */
  private static final int MAX_PASSWORD_BYTES = 72;

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokens;
  private final LocaleService locales;
  /** Hash compared against when the email is unknown, so both paths cost the same. */
  private final String dummyHash;

  DefaultIdentityService(
      UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens, LocaleService locales) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.locales = locales;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  @Override
  public AuthResult register(RegisterCommand command) {
    String email = User.normalizeEmail(command.email());
    if (users.existsByEmail(email)) {
      throw new ConflictException(ErrorCodes.EMAIL_TAKEN, "An account with this email already exists.");
    }
    String locale = validLocale(command.preferredLocale());
    Set<Role> roles = command.merchant() ? EnumSet.of(Role.CUSTOMER, Role.MERCHANT) : EnumSet.of(Role.CUSTOMER);
    User user =
        users.save(
            new User(
                email, passwordEncoder.encode(command.password()), command.firstName(), command.lastName(), roles, locale));
    return authResult(user);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResult login(String email, String password) {
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

  private AuthResult authResult(User user) {
    UserView view = view(user);
    return new AuthResult(tokens.issue(view), tokens.ttl(), view);
  }

  private static UserView view(User user) {
    return new UserView(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRoles().stream().map(Role::name).sorted().toList(),
        user.getPreferredLocale());
  }
}
