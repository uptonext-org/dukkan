package de.dukkan.identity.api;

import de.dukkan.identity.api.AuthDtos.AuthResponse;
import de.dukkan.identity.api.AuthDtos.LoginRequest;
import de.dukkan.identity.api.AuthDtos.RegisterRequest;
import de.dukkan.identity.api.AuthDtos.UpdateUserRequest;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.RegisterCommand;
import de.dukkan.identity.application.UserView;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
            new RegisterCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.merchant(),
                request.preferredLocale())));
  }

  @PostMapping("/auth/login")
  AuthResponse login(@RequestBody @Valid LoginRequest request) {
    return AuthResponse.from(identity.login(request.email(), request.password()));
  }

  @GetMapping("/me")
  UserView me(@CurrentSecurityContext SecurityContext security) {
    return identity.getUser(CurrentUser.id(security));
  }

  @PatchMapping("/me")
  UserView updateMe(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid UpdateUserRequest request) {
    return identity.updateProfile(
        CurrentUser.id(security), request.firstName(), request.lastName(), request.preferredLocale());
  }
}
