package de.dukkan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Without operator settings: no legal links yet, no terms to accept, payment simulated. */
class PlatformDefaultsIntegrationTest extends IntegrationTest {

  @Test
  void defaultsToASimulatedPaymentWithoutTermsToAccept() throws Exception {
    mvc.perform(get("/api/v1/platform"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.legal.imprintUrl").isEmpty())
        .andExpect(jsonPath("$.legal.termsVersion").isEmpty())
        .andExpect(jsonPath("$.payment").value("SIMULATED"));
    // Registration needs no acceptance then (every other integration test registers without one).
    registerUser();
  }
}
