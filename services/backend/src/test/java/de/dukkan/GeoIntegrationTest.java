package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class GeoIntegrationTest extends IntegrationTest {

  @Test
  void searchFindsStreetsWithHouseNumbersPostalCodesAndDistricts() throws Exception {
    mvc.perform(get("/api/v1/geo/search").param("q", "Lange Reihe 12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Lange Reihe 12, 20099 Hamburg (St. Georg)"))
        .andExpect(jsonPath("$[0].houseNumber").value("12"))
        .andExpect(jsonPath("$[0].district").value("St. Georg"))
        .andExpect(jsonPath("$[0].location.lat").isNumber());
    mvc.perform(get("/api/v1/geo/search").param("q", "20099"))
        .andExpect(jsonPath("$[0].label").value("20099 Hamburg (St. Georg)"));
    mvc.perform(get("/api/v1/geo/search").param("q", "eimsbuttel").param("limit", "3"))
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[*].district").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Eimsbüttel"))));
    mvc.perform(get("/api/v1/geo/search").param("q", "x")).andExpect(status().isBadRequest());
  }

  @Test
  void reverseGeocodingReturnsTheNearestPlaceOr404OutsideHamburg() throws Exception {
    mvc.perform(get("/api/v1/geo/reverse").param("lat", "53.5551").param("lng", "10.0128"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.district").value("St. Georg"))
        .andExpect(jsonPath("$.city").value("Hamburg"));
    mvc.perform(get("/api/v1/geo/reverse").param("lat", "48.1374").param("lng", "11.5755"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get("/api/v1/geo/reverse").param("lat", "91").param("lng", "10")).andExpect(status().isBadRequest());
  }

  @Test
  void addressesWithoutLocationAreGeocodedByStreetOrPostalCode() throws Exception {
    String token = registerUser();
    MvcResult street = address(token, Map.of("street", "Kardamomweg", "houseNumber", "3", "postalCode", "20099", "city", "Hamburg"));
    assertThat((Double) read(street, "$.location.lat")).isEqualTo(53.5562);

    MvcResult postalOnly = address(token, Map.of("street", "Unbekannte Gasse", "houseNumber", "1", "postalCode", "22767", "city", "Hamburg"));
    assertThat((Double) read(postalOnly, "$.location.lat")).isBetween(53.54, 53.56);

    MvcResult unknown = address(token, Map.of("street", "Marienplatz", "houseNumber", "1", "postalCode", "80331", "city", "München"));
    assertThat(object(unknown, "$.location")).isNull();

    MvcResult explicit =
        address(
            token,
            Map.of(
                "street", "Kardamomweg", "houseNumber", "3", "postalCode", "20099", "city", "Hamburg",
                "location", Map.of("lat", 53.5, "lng", 10.0)));
    assertThat((Double) read(explicit, "$.location.lat")).isEqualTo(53.5);
    assertThat((String) read(explicit, "$.countryCode")).isEqualTo("DE");
  }

  private MvcResult address(String token, Map<String, Object> body) throws Exception {
    return mvc.perform(json(auth(post("/api/v1/addresses"), token), toJson(body))).andExpect(status().isCreated()).andReturn();
  }
}
