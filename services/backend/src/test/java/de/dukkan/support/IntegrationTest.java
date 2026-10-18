package de.dukkan.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Base class: full application context, real PostgreSQL, MockMvc and API helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

  private static final AtomicInteger USERS = new AtomicInteger();

  @Autowired protected MockMvc mvc;
  @Autowired protected JsonMapper json;
  @Autowired protected MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.reset();
  }

  protected String toJson(Object value) {
    return json.writeValueAsString(value);
  }

  /** Thin wrapper over {@link JsonPath#read(String, String, com.jayway.jsonpath.Predicate...)}, which has the same generic signature. */
  @SuppressWarnings("TypeParameterUnusedInFormals")
  protected static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }

  protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  protected static String uniqueEmail() {
    return "user" + USERS.incrementAndGet() + "-" + UUID.randomUUID() + "@example.org";
  }

  /** Registers a fresh user and returns its access token. */
  protected String registerUser() throws Exception {
    return registerUser(uniqueEmail());
  }

  protected String registerUser(String email) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        toJson(
                            Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "firstName", "Test",
                                "lastName", "User"))))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.accessToken");
  }

  protected String login(String email) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("email", email, "password", "correct-horse-battery"))))
            .andExpect(status().isOk())
            .andReturn();
    return read(result, "$.accessToken");
  }

  protected String createAddress(String token, String postalCode) throws Exception {
    MvcResult result =
        mvc.perform(
                auth(post("/api/v1/addresses"), token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        toJson(
                            Map.of(
                                "label", "Home",
                                "street", "Musterweg",
                                "houseNumber", "1",
                                "postalCode", postalCode,
                                "city", "Neuss"))))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.id");
  }

  protected List<Map<String, Object>> slots(String token, String addressId) throws Exception {
    MvcResult result =
        mvc.perform(auth(get("/api/v1/delivery-slots").param("addressId", addressId), token))
            .andExpect(status().isOk())
            .andReturn();
    return read(result, "$");
  }

  protected String productIdBySku(String sku) throws Exception {
    MvcResult result =
        mvc.perform(get("/api/v1/products").param("size", "100"))
            .andExpect(status().isOk())
            .andReturn();
    List<String> ids = read(result, "$.items[?(@.sku == '" + sku + "')].id");
    if (ids.isEmpty()) {
      throw new IllegalStateException("No product with SKU " + sku);
    }
    return ids.getFirst();
  }

  protected MvcResult setQuantity(String token, String productId, int quantity) throws Exception {
    return mvc.perform(
            auth(put("/api/v1/cart/items/" + productId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + "}"))
        .andReturn();
  }

  protected MvcResult reserve(String token, Object slotId, String addressId) throws Exception {
    return mvc.perform(
            auth(put("/api/v1/cart/delivery"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("slotId", slotId, "addressId", addressId))))
        .andReturn();
  }
}
