package com.flowlog.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway 보안 통합 테스트.
 *
 * <ul>
 *   <li>경로 우회 형식 → 400 G0006, 차단 경로(H2 콘솔·백엔드 Actuator) → 404 G0001, 백엔드 호출 0건</li>
 *   <li>서비스 포트에 {@code /actuator/gateway}(라우트 쓰기 기능) 미노출</li>
 *   <li>관리 포트 {@code /actuator/prometheus} : 서킷 상태 / Rate Limit 거부 메트릭</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@Import(RedisTestContainerConfig.class)
class GatewaySecurityTest {

  private static final AtomicInteger BACKEND_CALLS = new AtomicInteger();
  private static final HttpServer BACKEND = startBackend();

  @LocalServerPort
  private int gatewayPort;

  @LocalManagementPort
  private int managementPort;

  private WebTestClient client;

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    String p = "spring.cloud.gateway.server.webflux.routes";
    String backend = "http://localhost:" + BACKEND.getAddress().getPort();
    // 운영과 같은 cm 라우트
    registry.add(p + "[0].id", () -> "cm");
    registry.add(p + "[0].uri", () -> backend);
    registry.add(p + "[0].predicates[0]", () -> "Path=/cm/**");
    registry.add(p + "[0].filters[0]", () -> "StripPrefix=1");
    registry.add(p + "[0].filters[1]", () -> "RateLimit");
    registry.add(p + "[0].filters[2].name", () -> "CircuitBreaker");
    registry.add(p + "[0].filters[2].args.name", () -> "cm");
    registry.add(p + "[0].filters[2].args.fallbackUri", () -> "forward:/fallback/cm");
    registry.add("app.gateway.rate-limit.replenish-rate", () -> "1000");
    registry.add("app.gateway.rate-limit.burst-capacity", () -> "1000");
  }

  @BeforeEach
  void setUp() {
    BACKEND_CALLS.set(0);
    client = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + gatewayPort)
        .responseTimeout(Duration.ofSeconds(10))
        .build();
  }

  @AfterAll
  static void tearDown() {
    BACKEND.stop(0);
  }

  @Test
  @DisplayName("정상 경로는 백엔드로 라우팅 (필터가 정상 요청을 막지 않음)")
  void normalPathRouted() {
    assertThat(RawHttp.send(gatewayPort, "GET", "/cm/api/v1/samples?page=0").status())
        .isEqualTo(200);
    assertThat(BACKEND_CALLS.get()).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0} → 404 G0001")
  @ValueSource(strings = {
      "/cm/h2-console", "/cm/h2-console/", "/cm/h2-console/login.do",
      "/cm/H2-Console/login.do", "/cm/actuator", "/cm/actuator/health", "/cm/ACTUATOR/env"})
  @DisplayName("차단 경로 (H2 콘솔 · 백엔드 Actuator) - 백엔드 호출 없이 404 G0001")
  void blockedPaths(String path) {
    RawHttp.Response response = RawHttp.send(gatewayPort, "GET", path);

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.body()).contains("\"messageId\":\"G0001\"");
    assertThat(BACKEND_CALLS.get()).isZero();
  }

  @ParameterizedTest(name = "{0} → 400 G0006")
  @ValueSource(strings = {
      "/cm/./h2-console", "/cm//h2-console", "/cm/x/../h2-console", "/cm/h2-console;x=1",
      "/cm/h2-console/..", "/cm/%2e/h2-console", "/cm/%2E%2E/h2-console", "/cm%2fh2-console",
      "/cm/h2-console%3b", "/cm/%252e/h2-console", "/cm/%5ch2-console"})
  @DisplayName("경로 우회 형식 - 백엔드 호출 없이 400 G0006")
  void bypassAttempts(String path) {
    RawHttp.Response response = RawHttp.send(gatewayPort, "GET", path);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body()).contains("\"messageId\":\"G0006\"");
    assertThat(BACKEND_CALLS.get()).isZero();
  }

  @Test
  @DisplayName("역슬래시 경로 - 서버(Netty)가 URI 파싱 단계에서 400 으로 거부, 백엔드 호출 없음")
  void backslashRejected() {
    assertThat(RawHttp.send(gatewayPort, "GET", "/cm/\\h2-console").status()).isEqualTo(400);
    assertThat(BACKEND_CALLS.get()).isZero();
  }

  @Test
  @DisplayName("서비스 포트에 /actuator/gateway 미노출 - 조회/라우트 추가/refresh 모두 404")
  void gatewayActuatorNotExposed() {
    client.get().uri("/actuator/gateway/routes").exchange()
        .expectStatus().isNotFound()
        .expectBody().jsonPath("$.messageId").isEqualTo("G0001");
    client.post().uri("/actuator/gateway/routes/evil")
        .header("Content-Type", "application/json")
        .bodyValue("{\"uri\":\"http://evil.example\",\"predicates\":[\"Path=/evil/**\"]}")
        .exchange()
        .expectStatus().isNotFound();
    client.post().uri("/actuator/gateway/refresh").exchange().expectStatus().isNotFound();
    client.get().uri("/evil/x").exchange().expectStatus().isNotFound();
  }

  @Test
  @DisplayName("관리 포트에도 /actuator/gateway 없음, health 는 관리 포트에서만")
  void managementPortEndpoints() {
    WebTestClient management = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + managementPort).build();
    management.get().uri("/actuator/health").exchange().expectStatus().isOk();
    management.get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
    client.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
  }

  @Test
  @DisplayName("관리 포트 /actuator/prometheus - 서킷 상태 메트릭 (application 태그)")
  void prometheusMetrics() {
    // 서킷 인스턴스(cm) 생성. (Rate Limit 거부 카운터 값은 GatewayResilienceTest 에서 검증)
    client.get().uri("/cm/api/ping").exchange().expectStatus().isOk();
    WebTestClient management = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + managementPort).build();
    String body = new String(management.get().uri("/actuator/prometheus").exchange()
        .expectStatus().isOk()
        .expectBody().returnResult().getResponseBodyContent(), StandardCharsets.UTF_8);

    assertThat(body)
        .contains("resilience4j_circuitbreaker_state{")
        .contains("name=\"cm\"")
        .contains("application=\"spring-gateway\"");
  }

  private static HttpServer startBackend() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> {
        BACKEND_CALLS.incrementAndGet();
        byte[] bytes = "{\"backend\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(bytes);
        }
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("스텁 백엔드 기동 실패", e);
    }
  }
}
