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
 * 화면(Vue SPA) 라우트 통합 테스트.
 *
 * <p>application.yml 의 라우트를 그대로 사용하고 대상 URI 만 스텁 서버로 바꾼다.
 *
 * <ul>
 *   <li>/cm/** → API 백엔드, 그 외 /** → 화면 서버 (경로 그대로)</li>
 *   <li>화면 라우트는 Rate Limit 미적용 (개발 서버 모듈 요청 다수)</li>
 *   <li>차단 경로(/actuator/**, /cm/h2-console/**)는 화면 라우트로도 넘어가지 않음</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class GatewayWebRouteTest {

  private static final Map<String, String> LAST_PATH = new ConcurrentHashMap<>();

  private static final HttpServer API = startServer("api");
  private static final HttpServer WEB = startServer("web");

  @LocalServerPort
  private int gatewayPort;

  private WebTestClient client;

  @DynamicPropertySource
  static void targets(DynamicPropertyRegistry registry) {
    registry.add("CM_SERVICE_URI", () -> "http://localhost:" + API.getAddress().getPort());
    registry.add("WEB_SERVICE_URI", () -> "http://localhost:" + WEB.getAddress().getPort());
    registry.add("app.gateway.rate-limit.replenish-rate", () -> "1");
    registry.add("app.gateway.rate-limit.burst-capacity", () -> "1");
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
    API.stop(0);
    WEB.stop(0);
  }

  @Test
  @DisplayName("/cm/** 는 API 로, 그 외 경로는 화면 서버로 경로 그대로 전달")
  void routesByPrefix() {
    client.get().uri("/support/notices/3?lang=en").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("web");
    assertThat(LAST_PATH.get("web")).isEqualTo("/support/notices/3");

    client.get().uri("/").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("web");

    client.get().uri("/cm/api/v1/auth/me").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("api");
    assertThat(LAST_PATH.get("api")).isEqualTo("/api/v1/auth/me");
  }

  @Test
  @DisplayName("화면 라우트는 Rate Limit 미적용 - 한도(1)를 넘는 연속 요청도 200")
  void webRoute_noRateLimit() {
    for (int i = 0; i < 5; i++) {
      client.get().uri("/assets/app-" + i + ".js").exchange().expectStatus().isOk();
    }
  }

  @Test
  @DisplayName("차단 경로는 화면 라우트로도 넘어가지 않음 - 404 G0001")
  void blockedPaths_notServedByWeb() {
    client.get().uri("/actuator/health").exchange()
        .expectStatus().isNotFound()
        .expectBody().jsonPath("$.messageId").isEqualTo("G0001");
    client.get().uri("/cm/h2-console").exchange()
        .expectStatus().isNotFound();
    assertThat(LAST_PATH).isEmpty();
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
    byte[] body = name.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
