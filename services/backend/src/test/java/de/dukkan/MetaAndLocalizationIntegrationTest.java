package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class MetaAndLocalizationIntegrationTest extends IntegrationTest {

  @Test
  void listsEnabledLocalesWithDirection() throws Exception {
    mvc.perform(get("/api/v1/locales"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.contains("de", "en", "ar")))
        .andExpect(jsonPath("$[2].direction").value("rtl"))
        .andExpect(jsonPath("$[2].nativeName").value("العربية"));
  }

  @Test
  void codeListsAreLocalisedAndCarryContentLanguage() throws Exception {
    mvc.perform(get("/api/v1/market-types").header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "ar"))
        .andExpect(jsonPath("$[?(@.code == 'BAKERY')].name").value("مخبز"));
    mvc.perform(get("/api/v1/market-types").header(HttpHeaders.ACCEPT_LANGUAGE, "en-GB,en;q=0.9"))
        .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en"))
        .andExpect(jsonPath("$[?(@.code == 'WEEKLY_MARKET')].name").value("Weekly market"));
    // Unsupported language → German.
    mvc.perform(get("/api/v1/cuisines").header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
        .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "de"))
        .andExpect(jsonPath("$[?(@.code == 'TURKISH')].name").value("Türkisch"));
    MvcResult cuisines = mvc.perform(get("/api/v1/cuisines")).andReturn();
    List<String> codes = read(cuisines, "$[*].code");
    assertThat(codes).contains("TURKISH", "ARABIC", "INDIAN", "ASIAN", "POLISH", "AFRICAN", "ITALIAN", "GERMAN");
  }

  @Test
  void preferredLocaleCanBeUpdatedAndMustBeEnabled() throws Exception {
    String token = registerUser();
    mvc.perform(json(auth(patch("/api/v1/me"), token), "{\"preferredLocale\":\"ar\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferredLocale").value("ar"))
        .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    mvc.perform(json(auth(patch("/api/v1/me"), token), "{\"preferredLocale\":\"xx\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("preferredLocale"))
        .andExpect(jsonPath("$.errors[0].code").value("SupportedLocale"));
    mvc.perform(auth(get("/api/v1/me"), token)).andExpect(jsonPath("$.preferredLocale").value("ar"));
  }
}
