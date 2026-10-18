package de.dukkan.identity.application;

import de.dukkan.identity.domain.User;
import de.dukkan.identity.infrastructure.UserRepository;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.InvalidCredentialsException;
import de.dukkan.shared.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultIdentityService implements IdentityService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokens;
  /** Hash compared against when the email is unknown, so both paths cost the same. */
  private final String dummyHash;

  DefaultIdentityService(UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  @Override
  public AuthResult register(RegisterCommand command) {
    String email = User.normalizeEmail(command.email());
    if (users.existsByEmail(email)) {
      throw new ConflictException(ErrorCodes.EMAIL_TAKEN, "An account with this email already exists.");
    }
    User user =
        users.save(
            new User(email, passwordEncoder.encode(command.password()), command.firstName(), command.lastName()));
    return authResult(user);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResult login(String email, String password) {
    Optional<User> user = users.findByEmail(User.normalizeEmail(email));
    String hash = user.map(User::getPasswordHash).orElse(dummyHash);
    boolean matches = passwordEncoder.matches(password, hash);
    if (user.isEmpty() || !matches) {
      throw new InvalidCredentialsException();
    }
    return authResult(user.orElseThrow());
  }

  @Override
  @Transactional(readOnly = true)
  public UserView getUser(UUID userId) {
    return users.findById(userId).map(DefaultIdentityService::view).orElseThrow(() -> new NotFoundException("User not found."));
  }

  private AuthResult authResult(User user) {
    UserView view = view(user);
    return new AuthResult(tokens.issue(view), tokens.ttl(), view);
  }

  private static UserView view(User user) {
    return new UserView(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
  }
}
