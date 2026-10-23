package de.dukkan.identity.api;

import de.dukkan.identity.api.AuthDtos.AuthResponse;
import de.dukkan.identity.api.AuthDtos.ChangePasswordRequest;
import de.dukkan.identity.api.AuthDtos.LoginRequest;
import de.dukkan.identity.api.AuthDtos.PasswordResetConfirmRequest;
import de.dukkan.identity.api.AuthDtos.PasswordResetRequest;
import de.dukkan.identity.api.AuthDtos.RefreshRequest;
import de.dukkan.identity.api.AuthDtos.RegisterRequest;
import de.dukkan.identity.api.AuthDtos.SocialSignInRequest;
import de.dukkan.identity.api.AuthDtos.UpdateUserRequest;
import de.dukkan.identity.api.AuthDtos.VerifyEmailRequest;
import de.dukkan.identity.application.IdTokenVerifier;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.RegisterCommand;
import de.dukkan.identity.application.SocialSignInCommand;
import de.dukkan.identity.application.UserView;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
                request.preferredLocale(),
                request.acceptedTermsVersion())));
  }

  @PostMapping("/auth/login")
  AuthResponse login(@RequestBody @Valid LoginRequest request) {
    return AuthResponse.from(identity.login(request.email(), request.password()));
  }

  @PostMapping("/auth/refresh")
  AuthResponse refresh(@RequestBody @Valid RefreshRequest request) {
    return AuthResponse.from(identity.refresh(request.refreshToken()));
  }

  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@RequestBody @Valid RefreshRequest request) {
    identity.logout(request.refreshToken());
  }

  /** Always 202, whether or not the account exists. */
  @PostMapping("/auth/password-reset")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void requestPasswordReset(@RequestBody @Valid PasswordResetRequest request) {
    identity.requestPasswordReset(request.email());
  }

  /** Confirms the email address with the token from the verification mail (B-39b). */
  @PostMapping("/auth/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
    identity.verifyEmail(request.token());
  }

  /** Sends the verification mail again (nothing happens when the address is already confirmed). */
  @PostMapping("/me/email-verification")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void resendVerification(@CurrentSecurityContext SecurityContext security) {
    identity.resendVerification(CurrentUser.id(security));
  }

  @PostMapping("/auth/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void confirmPasswordReset(@RequestBody @Valid PasswordResetConfirmRequest request) {
    identity.resetPassword(request.token(), request.newPassword());
  }

  @PostMapping("/auth/social")
  AuthResponse social(@RequestBody @Valid SocialSignInRequest request) {
    return AuthResponse.from(
        identity.socialSignIn(
            new SocialSignInCommand(
                IdTokenVerifier.Provider.valueOf(request.provider()),
                request.idToken(),
                request.nonce(),
                request.firstName(),
                request.lastName(),
                "MERCHANT".equals(request.accountType()),
                request.preferredLocale(),
                request.acceptedTermsVersion())));
  }

  @PutMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePassword(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid ChangePasswordRequest request) {
    identity.changePassword(CurrentUser.id(security), request.currentPassword(), request.newPassword());
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
