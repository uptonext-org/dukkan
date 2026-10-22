package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.CapturingAccountMailer;
import de.dukkan.support.IntegrationTest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** B-39b: new accounts confirm their email address with a single-use link. */
class EmailVerificationIntegrationTest extends IntegrationTest {

  @Autowired private CapturingAccountMailer mailer;

  private String token(String email) {
    String link = mailer.lastVerificationLink(email).orElseThrow();
    assertThat(link).startsWith("dukkan://verify-email?token=");
    return URLDecoder.decode(link.substring(link.indexOf("token=") + 6), StandardCharsets.UTF_8);
  }

  private void verify(String token, int expectedStatus) throws Exception {
    mvc.perform(json(post("/api/v1/auth/verify-email"), toJson(Map.of("token", token)))).andExpect(status().is(expectedStatus));
  }

  @Test
  void theWelcomeMailLinkConfirmsTheAddressOnce() throws Exception {
    String email = uniqueEmail();
    String access = register(email, "CUSTOMER");
    mvc.perform(auth(get("/api/v1/me"), access)).andExpect(jsonPath("$.emailVerified").value(false));

    String first = token(email);
    // A new mail replaces nothing: both links work until one is used.
    mvc.perform(auth(post("/api/v1/me/email-verification"), access)).andExpect(status().isAccepted());
    String second = token(email);
    assertThat(second).isNotEqualTo(first);

    verify(second, 204);
    mvc.perform(auth(get("/api/v1/me"), access)).andExpect(jsonPath("$.emailVerified").value(true));
    // All links are used up now.
    mvc.perform(json(post("/api/v1/auth/verify-email"), toJson(Map.of("token", first))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("token"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VERIFICATION_TOKEN"));
    // Confirmed accounts get no further mails.
    mvc.perform(auth(post("/api/v1/me/email-verification"), access)).andExpect(status().isAccepted());
    assertThat(token(email)).isEqualTo(second);
  }

  @Test
  void linksExpireAfterThreeDays() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    clock.advance(Duration.ofDays(3).plusMinutes(1));
    verify(token(email), 400);
    verify("not-a-token", 400);
  }

  @Nested
  @TestPropertySource(properties = "dukkan.security.auth.require-verified-email=true")
  class WhenRequired {

    @Test
    void unconfirmedAccountsCannotOrderOrOpenAMarket() throws Exception {
      String email = uniqueEmail();
      String merchant = register(email, "MERCHANT");
      mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(marketProfile("Unbestätigt-Markt"))))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
      verify(token(email), 204);
      mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(marketProfile("Bestätigt-Markt")))).andExpect(status().isCreated());

      String customer = registerUser();
      mvc.perform(json(auth(post("/api/v1/markets/" + marketId(ANATOLIA) + "/orders"), customer), "{}"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }
  }
}
