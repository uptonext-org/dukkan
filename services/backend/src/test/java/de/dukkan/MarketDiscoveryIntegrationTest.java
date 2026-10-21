package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code GET /markets} from St. Georg: radius, filters and sorts over the Hamburg seed markets. */
class MarketDiscoveryIntegrationTest extends IntegrationTest {

  private static MockHttpServletRequestBuilder nearStGeorg() {
    return get("/api/v1/markets").param("lat", String.valueOf(ST_GEORG_LAT)).param("lng", String.valueOf(ST_GEORG_LNG));
  }

  private List<String> slugs(MvcResult result) throws Exception {
    return read(result, "$.items[*].slug");
  }

  @Test
  void radiusSearchReturnsActiveMarketsNearestFirst() throws Exception {
    MvcResult oneKm = mvc.perform(nearStGeorg().param("radiusKm", "1")).andExpect(status().isOk()).andReturn();
    assertThat(slugs(oneKm)).containsExactly(ANATOLIA);

    MvcResult fiveKm = mvc.perform(nearStGeorg().param("radiusKm", "5")).andReturn();
    assertThat(slugs(fiveKm))
        .containsExactly(
            ANATOLIA,
            "smaki-polskie",
            "hofgut-lindenmarsch-stand",
            "mama-afrika-shop",
            BAB_AL_SHAM,
            "gruenkorb-bioladen",
            "masala-bazaar",
            "elbe-frische-supermarkt");
    List<Integer> distances = read(fiveKm, "$.items[*].distanceMeters");
    assertThat(distances).isSorted().allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(5000));
    assertThat((Integer) read(fiveKm, "$.items[0].distanceMeters")).isBetween(150, 250);
    assertThat((Integer) read(fiveKm, "$.totalItems")).isEqualTo(8);

    MvcResult defaultRadius = mvc.perform(nearStGeorg()).andReturn();
    assertThat(slugs(defaultRadius)).hasSize(11).doesNotContain("durstloescher-getraenke");
    MvcResult fifteenKm = mvc.perform(nearStGeorg().param("radiusKm", "15")).andReturn();
    assertThat(slugs(fifteenKm)).hasSize(12).contains("durstloescher-getraenke");
  }

  @Test
  void summariesCarryFulfilmentOpeningStateAndDeliveryReach() throws Exception {
    MvcResult result = mvc.perform(nearStGeorg().param("radiusKm", "1")).andReturn();
    Map<String, Object> anatolia = read(result, "$.items[0]");
    assertThat(anatolia).containsEntry("deliversToLocation", true).containsEntry("openNow", true);
    assertThat(anatolia.get("todayHours")).isEqualTo("08:00–20:00");
    assertThat(anatolia.get("nextOpeningAt")).isNull();
    assertThat((Integer) read(result, "$.items[0].activePromotionCount")).isPositive();
    assertThat((Integer) read(result, "$.items[0].fulfilment.deliveryFee.amountCents")).isEqualTo(290);
    assertThat((String) read(result, "$.items[0].type.name")).isEqualTo("Lebensmittelladen");
    assertThat(strings(result, "$.items[0].tags")).containsExactly("HALAL");
  }

  @Test
  void filtersByTypeCuisineFulfilmentOffersAndName() throws Exception {
    assertThat(slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("types", "BAKERY,BUTCHER")).andReturn()))
        .containsExactlyInAnyOrder(BAKERY, "fleischerei-al-baraka");
    assertThat(slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("cuisines", "POLISH")).andReturn()))
        .containsExactly("smaki-polskie");
    assertThat(slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("cuisines", "ARABIC,AFRICAN")).andReturn()))
        .containsExactlyInAnyOrder(ANATOLIA, BAB_AL_SHAM, "fleischerei-al-baraka", "mama-afrika-shop");

    List<String> pickup = slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("fulfilment", "PICKUP")).andReturn());
    assertThat(pickup).hasSize(10).doesNotContain("masala-bazaar", "durstloescher-getraenke");
    List<String> delivery = slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("fulfilment", "DELIVERY")).andReturn());
    assertThat(delivery).doesNotContain(BAKERY, "smaki-polskie", "hofgut-lindenmarsch-stand").contains("masala-bazaar");

    List<String> offers = slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("hasOffers", "true")).andReturn());
    assertThat(offers).hasSize(10).doesNotContain("hofgut-lindenmarsch-stand", "durstloescher-getraenke");

    assertThat(slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("q", "durstloscher")).andReturn()))
        .containsExactly("durstloescher-getraenke");
    mvc.perform(nearStGeorg().param("fulfilment", "BOAT")).andExpect(status().isBadRequest());
  }

  @Test
  void openNowUsesTheClock() throws Exception {
    // Monday 10:00: the weekly-market stall (Tue, Fri, Sat) is closed.
    List<String> openMonday = slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("openNow", "true")).andReturn());
    assertThat(openMonday).contains(ANATOLIA, BAB_AL_SHAM).doesNotContain("hofgut-lindenmarsch-stand");

    // Monday 21:00: only the supermarket (until 22:00) is open.
    clock.advance(java.time.Duration.ofHours(11));
    List<String> openLate = slugs(mvc.perform(nearStGeorg().param("radiusKm", "15").param("openNow", "true")).andReturn());
    assertThat(openLate).containsExactly("elbe-frische-supermarkt");
    MvcResult all = mvc.perform(nearStGeorg().param("radiusKm", "1")).andReturn();
    assertThat((Boolean) read(all, "$.items[0].openNow")).isFalse();
    assertThat((String) read(all, "$.items[0].nextOpeningAt")).contains("T08:00");
  }

  @Test
  void sortsByNameMinimumOrderAndDeliveryFee() throws Exception {
    List<String> names = read(mvc.perform(nearStGeorg().param("radiusKm", "15").param("sort", "name")).andReturn(), "$.items[*].name");
    assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);

    List<Integer> minimums =
        read(mvc.perform(nearStGeorg().param("radiusKm", "15").param("sort", "minimum_order")).andReturn(), "$.items[*].fulfilment.minimumOrderValue.amountCents");
    assertThat(minimums).isSorted();

    List<Map<String, Object>> fees =
        read(mvc.perform(nearStGeorg().param("radiusKm", "15").param("sort", "delivery_fee")).andReturn(), "$.items[*].fulfilment.deliveryFee");
    List<Integer> amounts = fees.stream().filter(java.util.Objects::nonNull).map(f -> (Integer) f.get("amountCents")).toList();
    assertThat(amounts).isSortedAccordingTo(Comparator.naturalOrder());
    assertThat(fees.subList(amounts.size(), fees.size())).containsOnlyNulls();

    mvc.perform(nearStGeorg().param("sort", "rating")).andExpect(status().isBadRequest());
  }

  @Test
  void pagingAndLocalisation() throws Exception {
    MvcResult page = mvc.perform(nearStGeorg().param("radiusKm", "15").param("size", "5").param("page", "2")).andReturn();
    assertThat((Integer) read(page, "$.totalItems")).isEqualTo(12);
    assertThat((Integer) read(page, "$.totalPages")).isEqualTo(3);
    assertThat(objects(page, "$.items")).hasSize(2);

    mvc.perform(nearStGeorg().param("radiusKm", "1").header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
        .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "ar"))
        .andExpect(jsonPath("$.items[0].type.name").value("بقالة"))
        .andExpect(jsonPath("$.items[0].cuisines[0].name").value("تركي"));
  }

  @Test
  void detailsIncludeHoursImprintAndLocalisedDescription() throws Exception {
    UUID id = marketId(BAB_AL_SHAM);
    mvc.perform(get("/api/v1/markets/" + id).param("lat", String.valueOf(ST_GEORG_LAT)).param("lng", String.valueOf(ST_GEORG_LNG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryLocale").value("ar"))
        .andExpect(jsonPath("$.distanceMeters").isNumber())
        .andExpect(jsonPath("$.imprint.legalName").value("Bab Al-Sham Feinkost UG (Demo)"))
        .andExpect(jsonPath("$.openingHours.length()").value(11))
        .andExpect(jsonPath("$.openingHours[0].opensAt").value("10:00"))
        // No Accept-Language: the market's primary locale (ar) wins over German.
        .andExpect(jsonPath("$.description").value("أطعمة شامية فاخرة: زعتر ولبنة ومعمول وقهوة بالهيل."));
    mvc.perform(get("/api/v1/markets/" + id).header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
        .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.startsWith("Levantinische Feinkost")))
        .andExpect(jsonPath("$.distanceMeters").doesNotExist());
    mvc.perform(get("/api/v1/markets/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void suspendedMarketsAreInvisible() throws Exception {
    String merchant = registerMerchant();
    UUID id = createMarket(merchant, "Gesperrter Testmarkt");
    MockHttpServletRequestBuilder munich = get("/api/v1/markets").param("lat", "48.1374").param("lng", "11.5755").param("radiusKm", "1");
    assertThat(strings(mvc.perform(munich).andReturn(), "$.items[*].id")).contains(id.toString());

    jdbc.sql("UPDATE market SET status = 'SUSPENDED' WHERE id = :id").param("id", id).update();

    assertThat(strings(mvc.perform(munich).andReturn(), "$.items[*].id")).doesNotContain(id.toString());
    mvc.perform(get("/api/v1/markets/" + id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/markets/" + id + "/products")).andExpect(status().isNotFound());
  }
}
