package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** An operator who publishes legal pages, requires the terms and lets customers pay the market. */
@TestPropertySource(
    properties = {
      "dukkan.platform.imprint-url=https://dukkan.example/impressum",
      "dukkan.platform.privacy-url=https://dukkan.example/datenschutz",
      "dukkan.platform.terms-url=https://dukkan.example/agb",
      "dukkan.platform.terms-version=2026-11",
      "dukkan.platform.support-email=hilfe@dukkan.example",
      "dukkan.platform.payment=ON_SITE"
    })
class PlatformIntegrationTest extends IntegrationTest {

  private String registration(String email, Object... extra) {
    Map<String, Object> body =
        new HashMap<>(Map.of("email", email, "password", PASSWORD, "firstName", "Ada", "lastName", "Lovelace"));
    for (int i = 0; i < extra.length; i += 2) {
      body.put((String) extra[i], extra[i + 1]);
    }
    return toJson(body);
  }

  @Test
  void tellsTheAppsWhereTheLegalPagesAreAndHowCustomersPay() throws Exception {
    mvc.perform(get("/api/v1/platform"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=300, public"))
        .andExpect(jsonPath("$.legal.imprintUrl").value("https://dukkan.example/impressum"))
        .andExpect(jsonPath("$.legal.privacyUrl").value("https://dukkan.example/datenschutz"))
        .andExpect(jsonPath("$.legal.termsUrl").value("https://dukkan.example/agb"))
        .andExpect(jsonPath("$.legal.termsVersion").value("2026-11"))
        .andExpect(jsonPath("$.supportEmail").value("hilfe@dukkan.example"))
        .andExpect(jsonPath("$.payment").value("ON_SITE"));
  }

  @Test
  void aNewAccountMustAcceptTheCurrentTermsAndTheAcceptanceIsRecorded() throws Exception {
    String email = uniqueEmail();
    mvc.perform(json(post("/api/v1/auth/register"), registration(email)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    mvc.perform(json(post("/api/v1/auth/register"), registration(email, "acceptedTermsVersion", "2025-01")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE email = ?").param(email).query(Long.class).single()).isZero();

    mvc.perform(json(post("/api/v1/auth/register"), registration(email, "acceptedTermsVersion", "2026-11")))
        .andExpect(status().isCreated());

    Map<String, Object> row =
        jdbc.sql("SELECT terms_version, terms_accepted_at FROM app_user WHERE email = ?").param(email).query().singleRow();
    assertThat(row.get("terms_version")).isEqualTo("2026-11");
    assertThat(row.get("terms_accepted_at")).isNotNull();
  }
}
