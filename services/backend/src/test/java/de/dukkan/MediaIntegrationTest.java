package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** B-32: merchant image upload (validation, ownership, dedupe) and public delivery. */
class MediaIntegrationTest extends IntegrationTest {

  private static final String KEY_URL = "^http://localhost/api/v1/media/[0-9a-f]{64}\\.";

  /** A colour per call so fixtures from different tests do not share keys unless intended. */
  private static Color randomColor() {
    return new Color(ThreadLocalRandom.current().nextInt(0x1000000));
  }

  private ResultActions upload(String token, UUID marketId, byte[] bytes, String declaredType, @Nullable String purpose)
      throws Exception {
    var request =
        multipart("/api/v1/merchant/markets/" + marketId + "/media")
            .file(new MockMultipartFile("file", "photo", declaredType, bytes));
    if (purpose != null) {
      request.param("purpose", purpose);
    }
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  private ResultActions upload(String token, UUID marketId, byte[] bytes, String declaredType) throws Exception {
    return upload(token, marketId, bytes, declaredType, "PRODUCT");
  }

  @Test
  void uploadsJpegPngAndWebpAndServesThemPublicly() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Fotomarkt");

    byte[] jpeg = TestImages.jpeg(320, 240, randomColor());
    MvcResult uploaded =
        upload(merchant, market, jpeg, "image/jpeg", "LOGO")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.width").value(320))
            .andExpect(jsonPath("$.height").value(240))
            .andExpect(jsonPath("$.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.sizeBytes").value(jpeg.length))
            .andReturn();
    String url = read(uploaded, "$.url");
    String key = read(uploaded, "$.key");
    assertThat(url).matches(KEY_URL + "jpg$").endsWith("/" + key);
    assertThat(uploaded.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(url);
    assertThat(OpenApiContract.load().requiredFields("MediaUpload"))
        .allSatisfy(field -> assertThat(object(uploaded, "$." + field)).as(field).isNotNull());

    MvcResult served =
        mvc.perform(get("/api/v1/media/" + key))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, Integer.toString(jpeg.length)))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().doesNotExist(HttpHeaders.PRAGMA))
            .andReturn();
    assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(jpeg);
    String etag = served.getResponse().getHeader(HttpHeaders.ETAG);
    mvc.perform(get("/api/v1/media/" + key).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());

    byte[] png = TestImages.png(40, 30, randomColor());
    MvcResult pngResult =
        upload(merchant, market, png, "image/png", "COVER")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.width").value(40))
            .andExpect(jsonPath("$.height").value(30))
            .andReturn();
    assertThat((String) read(pngResult, "$.url")).matches(KEY_URL + "png$");
    mvc.perform(get("/api/v1/media/" + read(pngResult, "$.key")))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));

    MvcResult webp =
        upload(merchant, market, TestImages.WEBP_1X1, "image/webp", "BANNER")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contentType").value("image/webp"))
            .andExpect(jsonPath("$.width").value(1))
            .andExpect(jsonPath("$.height").value(1))
            .andReturn();
    MvcResult webpServed =
        mvc.perform(get("/api/v1/media/" + read(webp, "$.key")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"))
            .andReturn();
    assertThat(webpServed.getResponse().getContentAsByteArray()).isEqualTo(TestImages.WEBP_1X1);
  }

  @Test
  void photoMetadataNeverReachesTheServedImage() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Datenschutzmarkt");
    byte[] plain = TestImages.jpeg(30, 20, randomColor());
    String secret = "GPS 53.5511N 9.9937E";
    byte[] comment = new byte[4 + secret.length()];
    comment[0] = (byte) 0xFF;
    comment[1] = (byte) 0xFE;
    comment[3] = (byte) (secret.length() + 2);
    System.arraycopy(secret.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), 0, comment, 4, secret.length());
    byte[] photo = new byte[plain.length + comment.length];
    System.arraycopy(plain, 0, photo, 0, 2);
    System.arraycopy(comment, 0, photo, 2, comment.length);
    System.arraycopy(plain, 2, photo, 2 + comment.length, plain.length - 2);

    MvcResult uploaded = upload(merchant, market, photo, "image/jpeg").andExpect(status().isCreated()).andReturn();
    assertThat((Integer) read(uploaded, "$.sizeBytes")).isEqualTo(plain.length);
    byte[] served = mvc.perform(get("/api/v1/media/" + read(uploaded, "$.key"))).andReturn().getResponse().getContentAsByteArray();
    assertThat(new String(served, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(secret);
    assertThat(served).isEqualTo(plain);
  }

  @Test
  void identicalBytesDeduplicateToTheSameKey() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Dedupemarkt");
    byte[] png = TestImages.png(16, 16, randomColor());
    String first = read(upload(merchant, market, png, "image/png", "PRODUCT").andExpect(status().isCreated()).andReturn(), "$.key");
    String second = read(upload(merchant, market, png, "application/octet-stream", "LOGO").andExpect(status().isCreated()).andReturn(), "$.key");
    // Another merchant uploading the same bytes gets the same object too.
    String other = registerMerchant();
    String third = read(upload(other, createMarket(other, "Andermarkt"), png, "image/png").andExpect(status().isCreated()).andReturn(), "$.key");
    assertThat(second).isEqualTo(first);
    assertThat(third).isEqualTo(first);
    byte[] otherPng = TestImages.png(16, 16, randomColor());
    String different = read(upload(merchant, market, otherPng, "image/png").andExpect(status().isCreated()).andReturn(), "$.key");
    assertThat(different).isNotEqualTo(first);
  }

  @Test
  void theDeclaredContentTypeIsIgnored() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Spoofmarkt");
    byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
    upload(merchant, market, html, "image/jpeg")
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);
    upload(merchant, market, svg, "image/png")
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    byte[] gif = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1);
    upload(merchant, market, gif, "image/webp").andExpect(status().isUnsupportedMediaType());
    // A real PNG declared as JPEG is stored as what it is.
    upload(merchant, market, TestImages.png(8, 8, randomColor()), "image/jpeg")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentType").value("image/png"))
        .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.endsWith(".png")));
  }

  @Test
  void corruptOversizedAndHugeImagesAreRejected() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Grenzmarkt");
    upload(merchant, market, TestImages.truncated(TestImages.png(300, 200, randomColor()), 0.6), "image/png")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
    byte[] jpegMagicThenGarbage = new byte[2048];
    Arrays.fill(jpegMagicThenGarbage, (byte) 0x41);
    jpegMagicThenGarbage[0] = (byte) 0xFF;
    jpegMagicThenGarbage[1] = (byte) 0xD8;
    jpegMagicThenGarbage[2] = (byte) 0xFF;
    upload(merchant, market, jpegMagicThenGarbage, "image/jpeg")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
    upload(merchant, market, new byte[0], "image/jpeg")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

    byte[] oversized = Arrays.copyOf(TestImages.jpeg(10, 10, randomColor()), 5 * 1024 * 1024 + 1);
    upload(merchant, market, oversized, "image/jpeg")
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

    upload(merchant, market, TestImages.pngHeaderOnly(8000, 6000), "image/png")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
  }

  @Test
  void requestShapeIsValidated() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Formmarkt");
    byte[] png = TestImages.png(8, 8, randomColor());
    upload(merchant, market, png, "image/png", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("purpose"));
    upload(merchant, market, png, "image/png", "AVATAR")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("purpose"));
    mvc.perform(
            multipart("/api/v1/merchant/markets/" + market + "/media")
                .param("purpose", "LOGO")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchant))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("file"));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/media"), merchant), "{\"purpose\":\"LOGO\"}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void uploadsAreOwnerChecked() throws Exception {
    String owner = registerMerchant();
    UUID market = createMarket(owner, "Eigenmarkt");
    byte[] png = TestImages.png(8, 8, randomColor());
    String otherMerchant = registerMerchant();
    upload(otherMerchant, market, png, "image/png").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    upload(otherMerchant, UUID.randomUUID(), png, "image/png").andExpect(status().isNotFound());
    String customer = registerUser();
    upload(customer, market, png, "image/png").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(
            multipart("/api/v1/merchant/markets/" + market + "/media")
                .file(new MockMultipartFile("file", "p.png", "image/png", png))
                .param("purpose", "LOGO"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownOrMalformedKeysAreNotFound() throws Exception {
    mvc.perform(get("/api/v1/media/" + "0".repeat(64) + ".jpg")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get("/api/v1/media/..%2F..%2Fetc%2Fpasswd")).andExpect(status().is4xxClientError());
    mvc.perform(get("/api/v1/media/" + "a".repeat(64) + ".svg")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/media/" + "A".repeat(64) + ".png")).andExpect(status().isNotFound());
  }

  @Test
  void uploadedUrlsWorkAsLogoCoverAndListingPhotos() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Bildermarkt");
    String logo = read(upload(merchant, market, TestImages.png(64, 64, randomColor()), "image/png", "LOGO").andReturn(), "$.url");
    String cover = read(upload(merchant, market, TestImages.jpeg(320, 120, randomColor()), "image/jpeg", "COVER").andReturn(), "$.url");
    Map<String, Object> profile = marketProfile("Bildermarkt");
    profile.put("logoUrl", logo);
    profile.put("coverImageUrl", cover);
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market), merchant), toJson(profile)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logoUrl").value(logo))
        .andExpect(jsonPath("$.coverImageUrl").value(cover));
    mvc.perform(get("/api/v1/markets/" + market)).andExpect(jsonPath("$.logoUrl").value(logo)).andExpect(jsonPath("$.coverImageUrl").value(cover));

    // Shared catalog product: the photo applies to this listing only.
    UUID listing = addListing(merchant, market, "Granatapfel", 199);
    String photo = read(upload(merchant, market, TestImages.jpeg(200, 200, randomColor()), "image/jpeg").andReturn(), "$.url");
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant), toJson(Map.of("imageUrl", photo))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").value(photo));
    mvc.perform(get("/api/v1/products/" + listing)).andExpect(jsonPath("$.imageUrl").value(photo));
    UUID seedListing = listingId(ANATOLIA, "Granatapfel");
    mvc.perform(get("/api/v1/products/" + seedListing)).andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.not(photo)));

    // Absent imageUrl keeps it; null removes it.
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant), "{\"available\":false}"))
        .andExpect(jsonPath("$.imageUrl").value(photo))
        .andExpect(jsonPath("$.available").value(false));
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant), "{\"imageUrl\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").doesNotExist());
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant), "{\"imageUrl\":\"not a url\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("imageUrl"));

    // Private product: created with a photo, replaced and removed through the listing.
    UUID category = jdbc.sql("SELECT id FROM category WHERE parent_id IS NULL ORDER BY sort_order LIMIT 1").query(UUID.class).single();
    Map<String, Object> newProduct = new HashMap<>();
    newProduct.put("name", Map.of("de", "Hausgemachtes Ajvar"));
    newProduct.put("unitSize", "300 g");
    newProduct.put("categoryId", category);
    newProduct.put("imageUrl", photo);
    MvcResult created =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                    toJson(Map.of("newProduct", newProduct, "price", Map.of("amountCents", 450, "currency", "EUR")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imageUrl").value(photo))
            .andExpect(jsonPath("$.privateProduct").value(true))
            .andReturn();
    String privateListing = read(created, "$.id");
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + privateListing), merchant), toJson(Map.of("imageUrl", logo))))
        .andExpect(jsonPath("$.imageUrl").value(logo));
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + privateListing), merchant), "{\"imageUrl\":null}"))
        .andExpect(jsonPath("$.imageUrl").doesNotExist());
    List<String> stored =
        jdbc.sql("SELECT coalesce(cp.image_url, '-') FROM market_product mp JOIN catalog_product cp ON cp.id = mp.catalog_product_id WHERE mp.id = :id")
            .param("id", UUID.fromString(privateListing))
            .query(String.class)
            .list();
    assertThat(stored).containsExactly("-");
  }
}
