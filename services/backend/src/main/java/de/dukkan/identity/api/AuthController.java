package de.dukkan.identity.api;

import de.dukkan.identity.api.AuthDtos.AuthResponse;
import de.dukkan.identity.api.AuthDtos.LoginRequest;
import de.dukkan.identity.api.AuthDtos.RegisterRequest;
import de.dukkan.identity.api.AuthDtos.UserResponse;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.RegisterCommand;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class AuthController {

  private final IdentityService identity;

  AuthController(IdentityService identity) {
    this.identity = identity;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  AuthResponse register(@RequestBody @Valid RegisterRequest request) {
    return AuthResponse.from(
        identity.register(
            new RegisterCommand(request.email(), request.password(), request.firstName(), request.lastName())));
  }

  @PostMapping("/auth/login")
  AuthResponse login(@RequestBody @Valid LoginRequest request) {
    return AuthResponse.from(identity.login(request.email(), request.password()));
  }

  @GetMapping("/me")
  UserResponse me(@CurrentSecurityContext SecurityContext security) {
    return UserResponse.from(identity.getUser(CurrentUser.id(security)));
  }
}
