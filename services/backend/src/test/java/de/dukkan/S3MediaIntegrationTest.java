package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/** B-32a: uploads go to an S3-compatible bucket (Adobe S3Mock in a container) and are served back. */
class S3MediaIntegrationTest extends IntegrationTest {

  private static final String BUCKET = "dukkan-media-test";

  @SuppressWarnings("resource")
  private static final GenericContainer<?> S3 =
      new GenericContainer<>("adobe/s3mock:5.2.3").withExposedPorts(9090).waitingFor(Wait.forHttp("/").forPort(9090));

  static {
    S3.start();
  }

  private static String endpoint() {
    return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
  }

  @DynamicPropertySource
  static void s3(DynamicPropertyRegistry registry) {
    registry.add("dukkan.media.storage", () -> "s3");
    registry.add("dukkan.media.s3.endpoint", S3MediaIntegrationTest::endpoint);
    registry.add("dukkan.media.s3.bucket", () -> BUCKET);
    registry.add("dukkan.media.s3.access-key", () -> "test");
    registry.add("dukkan.media.s3.secret-key", () -> "test-secret");
    registry.add("dukkan.media.s3.path-style", () -> "true");
    registry.add("dukkan.media.s3.create-bucket", () -> "true");
  }

  @AfterAll
  static void stop() {
    S3.stop();
  }

  @Test
  void uploadsLandInTheBucketAndAreServedBack() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "S3-Markt");
    byte[] png = TestImages.png(24, 16, new Color(0x3c6e47));

    MvcResult first = upload(merchant, market, png);
    String key = read(first, "$.key");
    assertThat(key).matches("^[0-9a-f]{64}\\.png$");
    // Same bytes, same key: the second upload is a no-op on the bucket.
    assertThat((String) read(upload(merchant, market, png), "$.key")).isEqualTo(key);

    MvcResult served =
        mvc.perform(get("/api/v1/media/" + key))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
            .andReturn();
    assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(png);
    mvc.perform(get("/api/v1/media/" + "0".repeat(64) + ".png")).andExpect(status().isNotFound());

    try (S3Client client =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint()))
            .region(Region.EU_CENTRAL_1)
            .forcePathStyle(true)
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test-secret")))
            .build()) {
      HeadObjectResponse head = client.headObject(b -> b.bucket(BUCKET).key(key.substring(0, 2) + "/" + key));
      assertThat(head.contentType()).isEqualTo("image/png");
      assertThat(head.contentLength()).isEqualTo(png.length);
      assertThat(head.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    }
  }

  private MvcResult upload(String token, UUID marketId, byte[] bytes) throws Exception {
    return mvc.perform(
            multipart("/api/v1/merchant/markets/" + marketId + "/media")
                .file(new MockMultipartFile("file", "photo.png", "image/png", bytes))
                .param("purpose", "PRODUCT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andReturn();
  }
}
