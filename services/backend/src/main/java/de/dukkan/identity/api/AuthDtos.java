package de.dukkan.identity.api;

import de.dukkan.identity.application.AuthResult;
import de.dukkan.identity.application.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request and response bodies of the auth endpoints. */
final class AuthDtos {

  private AuthDtos() {}

  record RegisterRequest(
      @Email @NotBlank @Size(max = 254) String email,
      @NotNull @Size(min = 10, max = 128) String password,
      @NotBlank @Size(min = 1, max = 100) String firstName,
      @NotBlank @Size(min = 1, max = 100) String lastName) {

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

  record UserResponse(UUID id, String email, String firstName, String lastName) {
    static UserResponse from(UserView user) {
      return new UserResponse(user.id(), user.email(), user.firstName(), user.lastName());
    }
  }

  record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    static AuthResponse from(AuthResult result) {
      return new AuthResponse(
          result.accessToken(), "Bearer", result.expiresIn().toSeconds(), UserResponse.from(result.user()));
    }
  }
}
