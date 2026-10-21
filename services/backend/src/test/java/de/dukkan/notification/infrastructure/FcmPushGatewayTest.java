package de.dukkan.notification.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import de.dukkan.notification.application.PushGateway.PushMessage;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.support.DukkanPropertiesFixture;
import de.dukkan.support.MutableClock;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** FCM HTTP v1 against a local stub of Google's token endpoint and the send API. */
class FcmPushGatewayTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Clock CLOCK = new MutableClock();

  @TempDir Path dir;
  private HttpServer server;
  private KeyPair keys;
  private final List<String> sentBodies = new CopyOnWriteArrayList<>();
  private final List<String> authHeaders = new CopyOnWriteArrayList<>();
  private final List<String> assertions = new CopyOnWriteArrayList<>();

  @BeforeEach
  void start() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keys = generator.generateKeyPair();
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/token",
        exchange -> {
          String form = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
          assertions.add(URLDecoder.decode(form.substring(form.indexOf("assertion=") + 10), UTF_8));
          respond(exchange, 200, "{\"access_token\":\"ya29.test\",\"expires_in\":3599,\"token_type\":\"Bearer\"}");
        });
    server.createContext(
        "/v1/projects/dukkan-test/messages:send",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
          sentBodies.add(body);
          authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
          boolean gone = body.contains("\"uninstalled-device\"");
          respond(exchange, gone ? 404 : 200, gone ? "{\"error\":{\"status\":\"NOT_FOUND\"}}" : "{\"name\":\"projects/dukkan-test/messages/1\"}");
        });
    server.start();
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private FcmPushGateway gateway() throws Exception {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    String pem =
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
    Path credentials = dir.resolve("service-account.json");
    Files.writeString(
        credentials,
        JSON.writeValueAsString(
            Map.of("type", "service_account", "client_email", "push@dukkan-test.iam.gserviceaccount.com", "private_key", pem, "token_uri", base + "/token")));
    DukkanProperties properties = DukkanPropertiesFixture.withFcm(new DukkanProperties.Fcm("dukkan-test", credentials, base, null));
    return new FcmPushGateway(properties, JSON, CLOCK);
  }

  @Test
  void signsATokenRequestSendsOneMessagePerDeviceAndReportsGoneDevices() throws Exception {
    FcmPushGateway gateway = gateway();
    assertThat(gateway.enabled()).isTrue();

    List<String> invalid =
        gateway.send(List.of("device-1", "uninstalled-device"), new PushMessage("Neue Bestellung", "DK-1 · 6,50 €", Map.of("orderId", "o-1")));

    assertThat(invalid).containsExactly("uninstalled-device");
    assertThat(sentBodies).hasSize(2);
    assertThat(authHeaders).containsOnly("Bearer ya29.test");
    JsonNode first = JSON.readTree(sentBodies.getFirst()).get("message");
    assertThat(first.get("token").asString()).isEqualTo("device-1");
    assertThat(first.get("notification").get("title").asString()).isEqualTo("Neue Bestellung");
    assertThat(first.get("data").get("orderId").asString()).isEqualTo("o-1");

    // One OAuth exchange, cached for the second message; the assertion is an RS256 JWT for FCM's scope.
    assertThat(assertions).hasSize(1);
    SignedJWT jwt = SignedJWT.parse(assertions.getFirst());
    JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keys.getPublic());
    assertThat(jwt.verify(verifier)).isTrue();
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("push@dukkan-test.iam.gserviceaccount.com");
    assertThat(jwt.getJWTClaimsSet().getStringClaim("scope")).isEqualTo("https://www.googleapis.com/auth/firebase.messaging");

    gateway.send(List.of("device-1"), new PushMessage("t", "b", Map.of()));
    assertThat(assertions).hasSize(1);
  }

  @Test
  void isDisabledWithoutConfiguration() {
    FcmPushGateway gateway =
        new FcmPushGateway(DukkanPropertiesFixture.withFcm(new DukkanProperties.Fcm(null, null, "https://fcm.googleapis.com", null)), JSON, CLOCK);
    assertThat(gateway.enabled()).isFalse();
    assertThat(gateway.send(List.of("x"), new PushMessage("t", "b", Map.of()))).isEmpty();
  }
}
