package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.CapturingAccountMailer;
import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OidcStub;
import de.dukkan.support.OpenApiContract;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** B-39: refresh token rotation, logout, password reset and change, Sign in with Google/Apple. */
class AuthSessionIntegrationTest extends IntegrationTest {

  @Autowired private CapturingAccountMailer mailer;

  private MvcResult login(String email, String password) throws Exception {
    return mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", password))))
        .andExpect(status().isOk())
        .andReturn();
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mvc.perform(json(post("/api/v1/auth/refresh"), toJson(Map.of("refreshToken", refreshToken))));
  }

  private ResultActions social(String provider, String idToken, Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new java.util.HashMap<>(extra);
    body.put("provider", provider);
    body.put("idToken", idToken);
    return mvc.perform(json(post("/api/v1/auth/social"), toJson(body)));
  }

  private String googleToken(String subject, Map<String, Object> claims) {
    return OidcStub.idToken(OidcStub.GOOGLE_ISSUER, OidcStub.GOOGLE_CLIENT, subject, clock.instant(), 600, claims);
  }

  @Test
  void refreshTokensRotateAndAReusedTokenEndsTheSignIn() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    MvcResult signedIn = login(email, PASSWORD);
    assertThat(CONTRACT.requiredFields("AuthResponse")).allSatisfy(f -> assertThat(objectAt(signedIn, f)).as(f).isNotNull());
    assertThat((Integer) read(signedIn, "$.expiresIn")).isEqualTo(30 * 24 * 3600); // test profile access TTL P30D
    assertThat((Integer) read(signedIn, "$.refreshExpiresIn")).isEqualTo(30 * 24 * 3600);
    String first = read(signedIn, "$.refreshToken");

    MvcResult rotated = refresh(first).andExpect(status().isOk()).andReturn();
    String second = read(rotated, "$.refreshToken");
    assertThat(second).isNotEqualTo(first);
    String access = read(rotated, "$.accessToken");
    mvc.perform(auth(get("/api/v1/me"), access)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

    // The old token is spent; presenting it again revokes the whole family, including `second`.
    refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    refresh(second).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // Other sign-ins of the same user are unaffected.
    String other = read(login(email, PASSWORD), "$.refreshToken");
    refresh(other).andExpect(status().isOk());
    refresh("not-a-token").andExpect(status().isUnauthorized());
    refresh("").andExpect(status().isBadRequest());
  }

  @Test
  void logoutEndsTheSignInAndRefreshTokensExpire() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    String token = read(login(email, PASSWORD), "$.refreshToken");
    mvc.perform(json(post("/api/v1/auth/logout"), toJson(Map.of("refreshToken", token)))).andExpect(status().isNoContent());
    refresh(token).andExpect(status().isUnauthorized());
    // Unknown tokens are ignored (logout is idempotent).
    mvc.perform(json(post("/api/v1/auth/logout"), toJson(Map.of("refreshToken", "unknown")))).andExpect(status().isNoContent());

    String later = read(login(email, PASSWORD), "$.refreshToken");
    clock.advance(Duration.ofDays(31));
    refresh(later).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void passwordResetByMailLinkSetsANewPasswordAndSignsOutEverywhere() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    String session = read(login(email, PASSWORD), "$.refreshToken");

    // Same answer for unknown accounts: no enumeration.
    mvc.perform(json(post("/api/v1/auth/password-reset"), toJson(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.org"))))
        .andExpect(status().isAccepted());
    mvc.perform(json(post("/api/v1/auth/password-reset"), toJson(Map.of("email", email.toUpperCase(java.util.Locale.ROOT)))))
        .andExpect(status().isAccepted());
    assertThat(mailer.lastLink(email)).hasValueSatisfying(link -> assertThat(link).startsWith("dukkan://reset-password?token="));
    String token = mailer.lastToken(email).orElseThrow();

    String newPassword = "brand-new-password-42";
    mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), toJson(Map.of("token", token, "newPassword", "short"))))
        .andExpect(status().isBadRequest());
    mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), toJson(Map.of("token", token, "newPassword", newPassword))))
        .andExpect(status().isNoContent());
    login(email, newPassword);
    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isUnauthorized());
    refresh(session).andExpect(status().isUnauthorized());

    // A link works once and expires.
    mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), toJson(Map.of("token", token, "newPassword", "another-password-99"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("token"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_RESET_TOKEN"));
    mvc.perform(json(post("/api/v1/auth/password-reset"), toJson(Map.of("email", email)))).andExpect(status().isAccepted());
    String expiring = mailer.lastToken(email).orElseThrow();
    clock.advance(Duration.ofMinutes(31));
    mvc.perform(json(post("/api/v1/auth/password-reset/confirm"), toJson(Map.of("token", expiring, "newPassword", "another-password-99"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changingThePasswordNeedsTheCurrentOne() throws Exception {
    String email = uniqueEmail();
    String access = register(email, "CUSTOMER");
    String session = read(login(email, PASSWORD), "$.refreshToken");

    mvc.perform(json(auth(put("/api/v1/me/password"), access), toJson(Map.of("currentPassword", "wrong-password-1", "newPassword", "changed-password-77"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
    mvc.perform(json(auth(put("/api/v1/me/password"), access), toJson(Map.of("currentPassword", PASSWORD, "newPassword", "changed-password-77"))))
        .andExpect(status().isNoContent());
    login(email, "changed-password-77");
    refresh(session).andExpect(status().isUnauthorized());
    mvc.perform(json(put("/api/v1/me/password"), toJson(Map.of("currentPassword", PASSWORD, "newPassword", "x".repeat(12)))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void signInWithGoogleCreatesTheAccountOnceAndThenSignsIn() throws Exception {
    String subject = "google-" + UUID.randomUUID();
    String email = uniqueEmail();
    Map<String, Object> claims = Map.of("email", email, "email_verified", true, "given_name", "Leyla", "family_name", "Kaya");

    MvcResult created =
        social("GOOGLE", googleToken(subject, claims), Map.of("accountType", "MERCHANT", "preferredLocale", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.firstName").value("Leyla"))
            .andExpect(jsonPath("$.user.lastName").value("Kaya"))
            .andExpect(jsonPath("$.user.preferredLocale").value("en"))
            .andReturn();
    assertThat(strings(created, "$.user.roles[*]")).containsExactly("CUSTOMER", "MERCHANT");
    String userId = read(created, "$.user.id");
    refresh(read(created, "$.refreshToken")).andExpect(status().isOk());

    // Same Google account again: same user, even if the email changed at Google.
    social("GOOGLE", googleToken(subject, Map.of("email", "renamed-" + email, "email_verified", true)), Map.of())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(userId))
        .andExpect(jsonPath("$.user.email").value(email));
    // The account has no known password, but "forgot password" can set one.
    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isUnauthorized());
  }

  @Test
  void signInWithAppleLinksAnExistingAccountByVerifiedEmail() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    String userId = read(mvc.perform(auth(get("/api/v1/me"), read(login(email, PASSWORD), "$.accessToken"))).andReturn(), "$.id");
    String nonce = "raw-nonce-" + UUID.randomUUID();
    String hashedNonce = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    // Apple sends email_verified as a string and the SHA-256 of the app's nonce.
    String token =
        OidcStub.idToken(OidcStub.APPLE_ISSUER, OidcStub.APPLE_CLIENT, "apple-" + UUID.randomUUID(), clock.instant(), 600,
            Map.of("email", email, "email_verified", "true", "nonce", hashedNonce));

    social("APPLE", token, Map.of("nonce", nonce)).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(userId));
    social("APPLE", token, Map.of("nonce", "other-nonce"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_SOCIAL_TOKEN"));
    social("APPLE", token, Map.of()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SOCIAL_TOKEN"));
  }

  @Test
  void namesCannotCarryTextOrLinksIntoTheMails() throws Exception {
    for (String name : List.of("Max\n\nIhr Konto wird gesperrt", "Max https://evil.example", "www.evil.example", "evil.example", "Max@evil")) {
      mvc.perform(
              json(
                  post("/api/v1/auth/register"),
                  toJson(Map.of("email", uniqueEmail(), "password", PASSWORD, "firstName", name, "lastName", "Muster"))))
          .andExpect(status().isBadRequest());
    }
    for (String name : List.of("Dr. Ali", "J.R.", "Anne-Marie", "عبد الرحمن", "O'Brien")) {
      mvc.perform(
              json(
                  post("/api/v1/auth/register"),
                  toJson(Map.of("email", uniqueEmail(), "password", PASSWORD, "firstName", name, "lastName", "Muster"))))
          .andExpect(status().isCreated());
    }
  }

  @Test
  void anAccountWithItsOwnPasswordNeedsItForDeletionEvenWithGoogleLinked() throws Exception {
    String email = uniqueEmail();
    String token = register(email, "CUSTOMER");
    confirmEmail(token);
    social("GOOGLE", googleToken("google-" + UUID.randomUUID(), Map.of("email", email, "email_verified", true)), Map.of())
        .andExpect(status().isOk());

    // A stolen session alone must not erase the account.
    mvc.perform(json(auth(delete("/api/v1/me"), token), "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("Incorrect"));
    mvc.perform(json(auth(delete("/api/v1/me"), token), toJson(Map.of("password", PASSWORD)))).andExpect(status().isNoContent());

    // An account created by Google sign-in has no password of its own and is deleted without one.
    String googleOnly =
        read(
            social("GOOGLE", googleToken("google-" + UUID.randomUUID(), Map.of("email", uniqueEmail(), "email_verified", true)), Map.of())
                .andExpect(status().isOk())
                .andReturn(),
            "$.accessToken");
    mvc.perform(json(auth(delete("/api/v1/me"), googleOnly), "{}")).andExpect(status().isNoContent());
  }

  @Test
  void anAccountSomeoneElsePreRegisteredLosesItsPasswordAndSessionsWhenTheOwnerSignsInWithGoogle() throws Exception {
    String email = uniqueEmail();
    // Someone registers the victim's address with their own password and never confirms it.
    register(email, "CUSTOMER");
    String squatterRefresh = read(login(email, PASSWORD), "$.refreshToken");

    social("GOOGLE", googleToken("google-" + UUID.randomUUID(), Map.of("email", email, "email_verified", true)), Map.of())
        .andExpect(status().isOk());

    refresh(squatterRefresh).andExpect(status().isUnauthorized());
    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsForeignExpiredOrUnverifiedTokens() throws Exception {
    String email = uniqueEmail();
    Map<String, Object> verified = Map.of("email", email, "email_verified", true);
    String subject = "google-" + UUID.randomUUID();

    String otherAudience = OidcStub.idToken(OidcStub.GOOGLE_ISSUER, "someone-else.apps.googleusercontent.com", subject, clock.instant(), 600, verified);
    social("GOOGLE", otherAudience, Map.of()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SOCIAL_TOKEN"));
    String otherIssuer = OidcStub.idToken("https://evil.example", OidcStub.GOOGLE_CLIENT, subject, clock.instant(), 600, verified);
    social("GOOGLE", otherIssuer, Map.of()).andExpect(status().isUnauthorized());
    // An Apple token sent as Google (issuer mismatch).
    String apple = OidcStub.idToken(OidcStub.APPLE_ISSUER, OidcStub.APPLE_CLIENT, subject, clock.instant(), 600, verified);
    social("GOOGLE", apple, Map.of()).andExpect(status().isUnauthorized());
    String expired = OidcStub.idToken(OidcStub.GOOGLE_ISSUER, OidcStub.GOOGLE_CLIENT, subject, clock.instant().minusSeconds(3600), 600, verified);
    social("GOOGLE", expired, Map.of()).andExpect(status().isUnauthorized());
    social("GOOGLE", "not.a.jwt", Map.of()).andExpect(status().isUnauthorized());

    social("GOOGLE", googleToken(subject, Map.of("email", email, "email_verified", false)), Map.of())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_MISSING"));
    social("FACEBOOK", googleToken(subject, verified), Map.of()).andExpect(status().isBadRequest());
  }

  private static final OpenApiContract CONTRACT = OpenApiContract.load();

  private static Object objectAt(MvcResult result, String field) throws Exception {
    return object(result, "$." + field);
  }
}
