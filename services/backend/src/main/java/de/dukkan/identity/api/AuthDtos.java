package de.dukkan.identity.api;

import de.dukkan.identity.application.AuthResult;
import de.dukkan.identity.application.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Request and response bodies of the auth endpoints. */
final class AuthDtos {

  private AuthDtos() {}

  record RegisterRequest(
      @Email @NotBlank @Size(max = 254) String email,
      @NotNull @Size(min = 10, max = 128) @MaxUtf8Bytes(72) String password,
      @NotBlank @Size(min = 1, max = 100) String firstName,
      @NotBlank @Size(min = 1, max = 100) String lastName,
      @Nullable @Pattern(regexp = "CUSTOMER|MERCHANT", message = "must be CUSTOMER or MERCHANT") String accountType,
      @Nullable String preferredLocale) {

    boolean merchant() {
      return "MERCHANT".equals(accountType);
    }

    @Override
    public String toString() {
      return "RegisterRequest[email=" + email + ", password=***]";
    }
  }

  record LoginRequest(@Email @NotBlank String email, @NotNull @Size(min = 1, max = 128) String password) {

    @Override
    public String toString() {
      return "LoginRequest[email=" + email + ", password=***]";
    }
  }

  record UpdateUserRequest(
      @Nullable @Size(min = 1, max = 100) String firstName,
      @Nullable @Size(min = 1, max = 100) String lastName,
      @Nullable String preferredLocale) {}

  record AuthResponse(String accessToken, String tokenType, long expiresIn, String refreshToken, long refreshExpiresIn, UserView user) {
    static AuthResponse from(AuthResult result) {
      return new AuthResponse(
          result.accessToken(), "Bearer", result.expiresIn().toSeconds(), result.refreshToken(), result.refreshExpiresIn().toSeconds(), result.user());
    }

    @Override
    public String toString() {
      return "AuthResponse[user=" + user.id() + ", tokens=***]";
    }
  }

  record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    @Override
    public String toString() {
      return "RefreshRequest[***]";
    }
  }

  record PasswordResetRequest(@Email @NotBlank @Size(max = 254) String email) {}

  record PasswordResetConfirmRequest(
      @NotBlank @Size(max = 200) String token, @NotNull @Size(min = 10, max = 128) @MaxUtf8Bytes(72) String newPassword) {
    @Override
    public String toString() {
      return "PasswordResetConfirmRequest[***]";
    }
  }

  record ChangePasswordRequest(
      @NotNull @Size(min = 1, max = 128) String currentPassword,
      @NotNull @Size(min = 10, max = 128) @MaxUtf8Bytes(72) String newPassword) {
    @Override
    public String toString() {
      return "ChangePasswordRequest[***]";
    }
  }

  record SocialSignInRequest(
      @NotNull @Pattern(regexp = "APPLE|GOOGLE", message = "must be APPLE or GOOGLE") String provider,
      @NotBlank @Size(max = 8192) String idToken,
      @Nullable @Size(max = 256) String nonce,
      @Nullable @Size(min = 1, max = 100) String firstName,
      @Nullable @Size(min = 1, max = 100) String lastName,
      @Nullable @Pattern(regexp = "CUSTOMER|MERCHANT", message = "must be CUSTOMER or MERCHANT") String accountType,
      @Nullable String preferredLocale) {
    @Override
    public String toString() {
      return "SocialSignInRequest[provider=" + provider + ", idToken=***]";
    }
  }
}
