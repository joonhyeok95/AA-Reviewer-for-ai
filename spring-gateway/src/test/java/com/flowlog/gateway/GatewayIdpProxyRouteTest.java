package com.flowlog.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 로그인 화면(Keycloak) 경유 라우트 (LAN 접속용, app.gateway.idp-proxy.enabled=true).
 *
 * <ul>
 *   <li>/realms/flowlog/**, /resources/** → Keycloak (경로 그대로)</li>
 *   <li>관리 콘솔(/admin/**)·다른 realm(/realms/master/**) 은 Keycloak 으로 전달하지 않음</li>
 *   <li>Keycloak 쿠키의 Secure 제거·SameSite=Lax (http://{IP} 에서 브라우저가 버리지 않도록)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-07
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class GatewayIdpProxyRouteTest {

  private static final Map<String, String> LAST_PATH = new ConcurrentHashMap<>();

  private static final HttpServer IDP = startServer("idp");
  private static final HttpServer WEB = startServer("web");

  @LocalServerPort
  private int gatewayPort;

  private WebTestClient client;

  @DynamicPropertySource
  static void targets(DynamicPropertyRegistry registry) {
    registry.add("app.gateway.idp-proxy.enabled", () -> "true");
    registry.add("IDP_SERVICE_URI", () -> "http://localhost:" + IDP.getAddress().getPort());
    registry.add("WEB_SERVICE_URI", () -> "http://localhost:" + WEB.getAddress().getPort());
  }

  @BeforeEach
  void setUp() {
    LAST_PATH.clear();
    client = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + gatewayPort)
        .responseTimeout(Duration.ofSeconds(10))
        .build();
  }

  @AfterAll
  static void tearDown() {
    IDP.stop(0);
    WEB.stop(0);
  }

  @Test
  @DisplayName("flowlog realm 과 로그인 화면 리소스는 Keycloak 으로 경로 그대로 전달")
  void realmAndResources_toIdp() {
    client.get().uri("/realms/flowlog/protocol/openid-connect/auth?client_id=flowlog-bff").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("idp");
    assertThat(LAST_PATH.get("idp")).isEqualTo("/realms/flowlog/protocol/openid-connect/auth");

    client.get().uri("/resources/abc/login/keycloak.v2/css/styles.css").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("idp");
  }

  @Test
  @DisplayName("Keycloak 쿠키는 Secure 를 빼고 SameSite=Lax 로 - HttpOnly·Path 는 유지")
  void keycloakCookies_httpFriendly() {
    client.get().uri("/realms/flowlog/protocol/openid-connect/auth").exchange()
        .expectStatus().isOk()
        .expectHeader().values("Set-Cookie", cookies -> {
          assertThat(cookies).hasSize(2);
          assertThat(cookies).allSatisfy(cookie -> assertThat(cookie)
              .doesNotContainIgnoringCase("secure")
              .contains("SameSite=Lax")
              .contains("Path=/realms/flowlog/"));
          assertThat(cookies.get(0)).contains("HttpOnly");
        });
  }

  @Test
  @DisplayName("관리 콘솔·master realm 은 Keycloak 으로 전달하지 않음")
  void adminAndMaster_notProxied() {
    client.get().uri("/admin/master/console/").exchange()
        .expectBody(String.class).isEqualTo("web");
    client.get().uri("/realms/master/protocol/openid-connect/auth").exchange()
        .expectBody(String.class).isEqualTo("web");
    assertThat(LAST_PATH).doesNotContainKey("idp");
  }

  private static HttpServer startServer(String name) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> respond(exchange, name));
      server.setExecutor(Executors.newFixedThreadPool(4));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("스텁 서버 기동 실패", e);
    }
  }

  private static void respond(HttpExchange exchange, String name) throws IOException {
    LAST_PATH.put(name, exchange.getRequestURI().getPath());
    if ("idp".equals(name)) {
      // Keycloak 실제 형식
      exchange.getResponseHeaders().add("Set-Cookie",
          "AUTH_SESSION_ID=abc;Version=1;Path=/realms/flowlog/;Secure;HttpOnly;SameSite=None");
      exchange.getResponseHeaders().add("Set-Cookie",
          "KC_RESTART=xyz;Version=1;Path=/realms/flowlog/;Max-Age=60;Secure;SameSite=None");
    }
    byte[] body = name.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
