package de.dukkan.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class: full application context, real PostgreSQL with the Hamburg seed data, MockMvc and
 * API helpers. All integration tests share one context and database, so tests create their own
 * users and use slots with spare capacity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

  public static final String PASSWORD = "correct-horse-battery";

  /** Seed market slugs. */
  public static final String ANATOLIA = "anatolia-frischmarkt";
  public static final String BAKERY = "backstube-sonnenkorn";
  public static final String BAB_AL_SHAM = "bab-al-sham-feinkost";

  /** St. Georg, Hamburg. */
  public static final double ST_GEORG_LAT = 53.5551;
  public static final double ST_GEORG_LNG = 10.0128;

  private static final AtomicInteger USERS = new AtomicInteger();

  @Autowired protected MockMvc mvc;
  @Autowired protected JsonMapper json;
  @Autowired protected MutableClock clock;
  @Autowired protected JdbcClient jdbc;

  @BeforeEach
  void resetClock() {
    clock.reset();
  }

  /** Sign in with Google/Apple against the local JWKS stub (OidcStub). */
  @DynamicPropertySource
  static void oidcProviders(DynamicPropertyRegistry registry) {
    registry.add("dukkan.security.auth.google.client-ids", () -> OidcStub.GOOGLE_CLIENT);
    registry.add("dukkan.security.auth.google.jwk-set-uri", OidcStub::jwkSetUri);
    registry.add("dukkan.security.auth.apple.client-ids", () -> OidcStub.APPLE_CLIENT);
    registry.add("dukkan.security.auth.apple.jwk-set-uri", OidcStub::jwkSetUri);
  }

  protected String toJson(Object value) {
    return json.writeValueAsString(value);
  }

  /** Thin wrapper over {@link JsonPath#read(String, String, com.jayway.jsonpath.Predicate...)}. */
  @SuppressWarnings("TypeParameterUnusedInFormals")
  protected static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }

  protected static List<String> strings(MvcResult result, String path) throws Exception {
    return read(result, path);
  }

  protected static List<Integer> ints(MvcResult result, String path) throws Exception {
    return read(result, path);
  }

  protected static List<Boolean> booleans(MvcResult result, String path) throws Exception {
    return read(result, path);
  }

  protected static List<Object> objects(MvcResult result, String path) throws Exception {
    return read(result, path);
  }

  protected static List<Map<String, Object>> maps(MvcResult result, String path) throws Exception {
    return read(result, path);
  }

  protected static @Nullable Object object(MvcResult result, String path) throws Exception {
    return read(result, path);
  }

  protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  protected static String uniqueEmail() {
    return "user" + USERS.incrementAndGet() + "-" + UUID.randomUUID() + "@example.org";
  }

  // ------------------------------------------------------------------ accounts

  /** Registers a fresh customer and returns its access token. */
  protected String registerUser() throws Exception {
    return register(uniqueEmail(), "CUSTOMER");
  }

  /** Registers a fresh merchant (roles CUSTOMER + MERCHANT) and returns its access token. */
  protected String registerMerchant() throws Exception {
    return register(uniqueEmail(), "MERCHANT");
  }

  protected String register(String email, String accountType) throws Exception {
    MvcResult result =
        mvc.perform(
                json(
                    post("/api/v1/auth/register"),
                    toJson(
                        Map.of(
                            "email", email,
                            "password", PASSWORD,
                            "firstName", "Test",
                            "lastName", "User",
                            "accountType", accountType))))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.accessToken");
  }

  /** Marks the account's e-mail address as confirmed (as the link in the welcome mail does). */
  protected void confirmEmail(String token) throws Exception {
    jdbc.sql("UPDATE app_user SET email_verified_at = now() WHERE id = ?").param(userId(token)).update();
  }

  protected String login(String email) throws Exception {
    MvcResult result =
        mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    return read(result, "$.accessToken");
  }

  protected UUID userId(String token) throws Exception {
    MvcResult me = mvc.perform(auth(get("/api/v1/me"), token)).andExpect(status().isOk()).andReturn();
    return UUID.fromString(read(me, "$.id"));
  }

  // ------------------------------------------------------------------ seed lookups

  protected UUID marketId(String slug) {
    return jdbc.sql("SELECT id FROM market WHERE slug = :slug").param("slug", slug).query(UUID.class).single();
  }

  /** The listing of a seed product (by German name) in a market. */
  protected UUID listingId(String marketSlug, String germanName) {
    return jdbc.sql(
            """
            SELECT mp.id FROM market_product mp
              JOIN market m ON m.id = mp.market_id
              JOIN catalog_product_translation t ON t.catalog_product_id = mp.catalog_product_id AND t.locale = 'de'
             WHERE m.slug = :slug AND t.name = :name AND mp.deleted_at IS NULL
            """)
        .param("slug", marketSlug)
        .param("name", germanName)
        .query(UUID.class)
        .single();
  }

  /** Any available listing of the market whose price is at least {@code minCents}. */
  protected UUID availableListing(String marketSlug, long minCents) {
    return jdbc.sql(
            """
            SELECT mp.id FROM market_product mp JOIN market m ON m.id = mp.market_id
             WHERE m.slug = :slug AND mp.available AND mp.deleted_at IS NULL AND mp.price_cents >= :min
             ORDER BY mp.price_cents, mp.id LIMIT 1
            """)
        .param("slug", marketSlug)
        .param("min", minCents)
        .query(UUID.class)
        .single();
  }

  // ------------------------------------------------------------------ customer actions

  protected String createAddress(String token, String street, String houseNumber, String postalCode) throws Exception {
    MvcResult result =
        mvc.perform(
                json(
                    auth(post("/api/v1/addresses"), token),
                    toJson(
                        Map.of(
                            "label", "Home",
                            "street", street,
                            "houseNumber", houseNumber,
                            "postalCode", postalCode,
                            "city", "Hamburg"))))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.id");
  }

  protected List<Map<String, Object>> slots(@Nullable String token, UUID marketId, String type, @Nullable String addressId)
      throws Exception {
    MockHttpServletRequestBuilder request = get("/api/v1/markets/" + marketId + "/slots").param("type", type);
    if (addressId != null) {
      request = request.param("addressId", addressId);
    }
    if (token != null) {
      request = auth(request, token);
    }
    MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
    return read(result, "$");
  }

  /** The first available slot with at least {@code minRemaining} free capacity. */
  protected String availableSlot(List<Map<String, Object>> slots, int minRemaining) {
    return slots.stream()
        .filter(s -> Boolean.TRUE.equals(s.get("available")) && ((Number) s.get("remainingCapacity")).intValue() >= minRemaining)
        .map(s -> (String) s.get("id"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No available slot"));
  }

  protected MvcResult setQuantity(String token, UUID marketId, UUID listingId, int quantity) throws Exception {
    return mvc.perform(
            json(
                auth(put("/api/v1/markets/" + marketId + "/cart/items/" + listingId), token),
                "{\"quantity\":" + quantity + "}"))
        .andReturn();
  }

  protected MvcResult reserve(String token, UUID marketId, String type, String slotId, @Nullable String addressId)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("type", type);
    body.put("slotId", slotId);
    body.put("addressId", addressId);
    return mvc.perform(json(auth(put("/api/v1/markets/" + marketId + "/cart/fulfilment"), token), toJson(body))).andReturn();
  }

  protected MvcResult placeOrder(String token, UUID marketId) throws Exception {
    return mvc.perform(auth(post("/api/v1/markets/" + marketId + "/orders"), token)).andReturn();
  }

  /** Fills the cart with enough of {@code listingId} to reach {@code targetCents}, reserves pickup and orders. */
  protected String orderPickup(String token, UUID marketId, UUID listingId, int quantity) throws Exception {
    setQuantity(token, marketId, listingId, quantity);
    String slot = availableSlot(slots(null, marketId, "PICKUP", null), 1);
    MvcResult reserved = reserve(token, marketId, "PICKUP", slot, null);
    if (reserved.getResponse().getStatus() != 200) {
      throw new IllegalStateException("Reservation failed: " + reserved.getResponse().getContentAsString());
    }
    MvcResult placed = placeOrder(token, marketId);
    if (placed.getResponse().getStatus() != 201) {
      throw new IllegalStateException("Order failed: " + placed.getResponse().getContentAsString());
    }
    return read(placed, "$.id");
  }

  // ------------------------------------------------------------------ merchant actions

  /** A valid market profile far away from Hamburg (Munich) so that test markets do not affect Hamburg searches. */
  protected Map<String, Object> marketProfile(String name) {
    Map<String, Object> profile = new HashMap<>();
    profile.put("name", name);
    profile.put("typeCode", "GROCERY");
    profile.put("cuisineCodes", List.of("TURKISH"));
    profile.put("description", Map.of("de", "Testmarkt", "en", "Test market"));
    profile.put("primaryLocale", "de");
    profile.put("addressLine", "Testweg 1, 80331 München");
    profile.put("location", Map.of("lat", 48.1374, "lng", 11.5755));
    profile.put("tags", List.of("HALAL"));
    Map<String, Object> fulfilment = new HashMap<>();
    fulfilment.put("delivery", true);
    fulfilment.put("pickup", true);
    fulfilment.put("deliveryRadiusKm", 3.0);
    fulfilment.put("deliveryFee", Map.of("amountCents", 290, "currency", "EUR"));
    fulfilment.put("freeDeliveryFrom", Map.of("amountCents", 4000, "currency", "EUR"));
    fulfilment.put("minimumOrderValue", Map.of("amountCents", 500, "currency", "EUR"));
    profile.put("fulfilment", fulfilment);
    profile.put(
        "openingHours",
        List.of(1, 2, 3, 4, 5, 6).stream().map(d -> Map.of("dayOfWeek", d, "opensAt", "08:00", "closesAt", "20:00")).toList());
    profile.put("phone", "+49 40 1234567");
    profile.put("legalName", name + " GmbH");
    return profile;
  }

  /** Creates a market with {@link #marketProfile} and switches online orders on (new markets start without). */
  protected UUID createMarket(String merchantToken, String name) throws Exception {
    MvcResult result =
        mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchantToken), toJson(marketProfile(name))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID marketId = UUID.fromString(read(result, "$.id"));
    updateOrderSettings(merchantToken, marketId, Map.of());
    return marketId;
  }

  /** Default ordering settings with orders switched on, overridden by {@code changes}. */
  protected Map<String, Object> orderSettings(Map<String, Object> changes) {
    Map<String, Object> settings = new HashMap<>();
    settings.put("ordersEnabled", true);
    settings.put("deliveryHours", List.of());
    settings.put("autoAccept", false);
    settings.put("paymentMethods", List.of("CASH"));
    settings.putAll(changes);
    return settings;
  }

  protected MvcResult updateOrderSettings(String merchantToken, UUID marketId, Map<String, Object> changes) throws Exception {
    return mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + marketId + "/order-settings"), merchantToken),
                toJson(orderSettings(changes))))
        .andExpect(status().isOk())
        .andReturn();
  }

  /** Adds a shared catalog product (by German name) to a merchant's market and returns the listing id. */
  protected UUID addListing(String merchantToken, UUID marketId, String germanName, long priceCents) throws Exception {
    UUID catalogProductId =
        jdbc.sql(
                "SELECT catalog_product_id FROM catalog_product_translation t JOIN catalog_product cp ON cp.id = t.catalog_product_id"
                    + " WHERE t.locale = 'de' AND t.name = :name AND cp.owner_market_id IS NULL")
            .param("name", germanName)
            .query(UUID.class)
            .single();
    MvcResult result =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + marketId + "/listings"), merchantToken),
                    toJson(
                        Map.of(
                            "catalogProductId", catalogProductId,
                            "price", Map.of("amountCents", priceCents, "currency", "EUR")))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(read(result, "$.id"));
  }
}
