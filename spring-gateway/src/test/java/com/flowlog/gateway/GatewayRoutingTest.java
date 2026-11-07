package com.flowlog.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway 라우팅 통합 테스트.
 *
 * <p>실제 Gateway(Netty, 랜덤 포트)를 띄우고, 백엔드는 JDK HttpServer 스텁으로 대체해
 * "백엔드가 실제로 받은 요청"(경로, 헤더, 본문)을 검증한다.
 *
 * <ul>
 *   <li>cm   : /cm/**   → 스텁 백엔드 (StripPrefix=1, RateLimit, CircuitBreaker - 운영과 동일 구성)</li>
 *   <li>down : /down/** → 닫힌 포트 (서킷브레이커 없는 라우트의 연결 실패 시나리오)</li>
 * </ul>
 *
 * <p>Rate Limit / 서킷 동작 자체는 {@link GatewayResilienceTest} 에서 검증한다.
 * (이 테스트는 한도를 넉넉히 두어 라우팅만 검증)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class GatewayRoutingTest {

  /** 스텁 백엔드가 마지막으로 받은 요청 정보. */
  private static final Map<String, String> LAST_REQUEST = new ConcurrentHashMap<>();

  private static final HttpServer BACKEND = startBackend();
  private static final int CLOSED_PORT = findClosedPort();

  @LocalServerPort
  private int gatewayPort;

  private WebTestClient client;

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    // 리스트 속성은 병합되지 않고 통째로 대체되므로 라우트 전체를 정의한다.
    String prefix = "spring.cloud.gateway.server.webflux.";
    registry.add(prefix + "routes[0].id", () -> "cm");
    registry.add(prefix + "routes[0].uri",
        () -> "http://localhost:" + BACKEND.getAddress().getPort());
    registry.add(prefix + "routes[0].predicates[0]", () -> "Path=/cm/**");
    registry.add(prefix + "routes[0].filters[0]", () -> "StripPrefix=1");
    registry.add(prefix + "routes[0].filters[1]", () -> "RateLimit");
    registry.add(prefix + "routes[0].filters[2].name", () -> "CircuitBreaker");
    registry.add(prefix + "routes[0].filters[2].args.name", () -> "cm");
    registry.add(prefix + "routes[0].filters[2].args.fallbackUri", () -> "forward:/fallback/cm");
    registry.add(prefix + "routes[1].id", () -> "down");
    registry.add(prefix + "routes[1].uri", () -> "http://localhost:" + CLOSED_PORT);
    registry.add(prefix + "routes[1].predicates[0]", () -> "Path=/down/**");
    registry.add(prefix + "routes[1].filters[0]", () -> "StripPrefix=1");
    registry.add(prefix + "httpclient.response-timeout", () -> "1s");
    registry.add("resilience4j.timelimiter.instances.cm.timeout-duration", () -> "1s");
    registry.add("app.gateway.rate-limit.replenish-rate", () -> "1000");
    registry.add("app.gateway.rate-limit.burst-capacity", () -> "1000");
  }

  @BeforeEach
  void setUp() {
    LAST_REQUEST.clear();
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
  @DisplayName("/cm/** → /cm 제거 후 백엔드로 라우팅 (경로, 쿼리스트링 유지)")
  void stripPrefixRouting() {
    client.get().uri("/cm/api/v1/auth/me?lang=ko").exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.backend").isEqualTo("ok");

    assertThat(LAST_REQUEST.get("path")).isEqualTo("/api/v1/auth/me");
    assertThat(LAST_REQUEST.get("query")).isEqualTo("lang=ko");
  }

  @Test
  @DisplayName("X-Forwarded-Prefix(/cm)·Host·Proto 를 백엔드에 전달 - 백엔드가 외부 URL 을 인식")
  void forwardedHeaders() {
    client.get().uri("/cm/api/v1/samples").exchange().expectStatus().isOk();

    assertThat(LAST_REQUEST.get("x-forwarded-prefix")).isEqualTo("/cm");
    assertThat(LAST_REQUEST.get("x-forwarded-host")).isEqualTo("localhost:" + gatewayPort);
    assertThat(LAST_REQUEST.get("x-forwarded-proto")).isEqualTo("http");
    assertThat(LAST_REQUEST.get("forwarded")).isNull();
  }

  @Test
  @DisplayName("TLS 종료 LB(ALB) 뒤 - 신뢰 프록시가 보낸 Proto https·Port 443 이 백엔드에 먼저 전달, Forwarded 없음")
  void forwardedHeaders_tlsTerminatingLoadBalancer() {
    client.get().uri("/cm/api/v1/samples")
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Port", "443")
        .exchange().expectStatus().isOk();

    // 백엔드(ForwardedHeaderFilter)는 첫 값을 쓴다 → https://{host}/... (Forwarded 가 있으면 그것을 우선해 http 가 됨)
    assertThat(LAST_REQUEST.get("x-forwarded-proto")).startsWith("https");
    assertThat(LAST_REQUEST.get("x-forwarded-port")).startsWith("443");
    assertThat(LAST_REQUEST.get("forwarded")).isNull();
  }

  @Test
  @DisplayName("신뢰 프록시(loopback)가 보낸 위조 Host/Prefix 는 Gateway 값으로 덮어쓰고 Forwarded 는 제거 - 단일 값")
  void forwardedHeaders_spoofedFromTrustedProxy() {
    client.get().uri("/cm/api/v1/samples")
        .header("X-Forwarded-Host", "evil.example")
        .header("X-Forwarded-Prefix", "/evil")
        .header("Forwarded", "host=evil.example;proto=https")
        .exchange().expectStatus().isOk();

    // 수정 전: x-forwarded-host="evil.example,localhost:{port}", x-forwarded-prefix="/evil,/cm"
    //         → 백엔드(ForwardedHeaderFilter)는 첫 Host 와 prefix 를 이어 붙인 "/evil/cm" 을 사용
    assertThat(LAST_REQUEST.get("x-forwarded-host")).isEqualTo("localhost:" + gatewayPort);
    assertThat(LAST_REQUEST.get("x-forwarded-prefix")).isEqualTo("/cm");
    // Forwarded(RFC 7239)는 전달하지 않는다 (백엔드가 우선 적용하므로 LB 경유 위조 차단)
    assertThat(LAST_REQUEST.get("forwarded")).isNull();
  }

  @Test
  @DisplayName("X-Trace-Id - 없으면 생성해 백엔드/응답에 동일 값 전달, 있으면 재사용")
  void traceIdPropagation() {
    String generated = client.get().uri("/cm/ping").exchange()
        .expectStatus().isOk()
        .returnResult(String.class).getResponseHeaders().getFirst("X-Trace-Id");
    assertThat(generated).isNotBlank().isEqualTo(LAST_REQUEST.get("x-trace-id"));

    client.get().uri("/cm/ping").header("X-Trace-Id", "trace-from-client-01").exchange()
        .expectHeader().valueEquals("X-Trace-Id", "trace-from-client-01");
    assertThat(LAST_REQUEST.get("x-trace-id")).isEqualTo("trace-from-client-01");
  }

  @Test
  @DisplayName("Authorization 헤더 · 요청 본문 · Method 를 그대로 전달 (Bearer 인증 프록시)")
  void forwardsAuthorizationAndBody() {
    client.post().uri("/cm/api/v1/samples")
        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"gw\"}")
        .exchange()
        .expectStatus().isOk();

    assertThat(LAST_REQUEST.get("method")).isEqualTo("POST");
    assertThat(LAST_REQUEST.get("authorization")).isEqualTo("Bearer test-token");
    assertThat(LAST_REQUEST.get("body")).isEqualTo("{\"name\":\"gw\"}");
  }

  @Test
  @DisplayName("백엔드의 오류 응답(401 표준 응답)은 Gateway 가 변경하지 않고 그대로 전달")
  void backendErrorPassThrough() {
    client.get().uri("/cm/status/401").exchange()
        .expectStatus().isUnauthorized()
        .expectBody().jsonPath("$.messageId").isEqualTo("C0009");
  }

  @Test
  @DisplayName("매칭 라우트 없음 - 404, G0001 표준 응답")
  void noRoute() {
    client.get().uri("/unknown/path").exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.httpStatus").isEqualTo(404)
        .jsonPath("$.messageId").isEqualTo("G0001")
        .jsonPath("$.messageTitle").isEqualTo("요청 경로 없음")
        .jsonPath("$.locale").isEqualTo("ko_KR")
        .jsonPath("$.path").isEqualTo("/unknown/path")
        .jsonPath("$.timestamp").exists();
  }

  @Test
  @DisplayName("다국어 - Accept-Language: en-US 이면 영문 메시지")
  void noRoute_english() {
    client.get().uri("/unknown").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US").exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.locale").isEqualTo("en_US")
        .jsonPath("$.messageTitle").isEqualTo("No route");
  }

  @Test
  @DisplayName("백엔드 연결 실패(서킷브레이커 없는 라우트) - 503, G0002")
  void backendDown() {
    client.get().uri("/down/api").exchange()
        .expectStatus().isEqualTo(503)
        .expectBody().jsonPath("$.messageId").isEqualTo("G0002");
  }

  @Test
  @DisplayName("백엔드 응답 시간 초과(TimeLimiter 1s) - 서킷 fallback 504, G0003")
  void backendTimeout() {
    client.get().uri("/cm/slow").exchange()
        .expectStatus().isEqualTo(504)
        .expectBody().jsonPath("$.messageId").isEqualTo("G0003");
  }

  // ---------------------------------------------------------------- stub backend

  private static HttpServer startBackend() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", GatewayRoutingTest::handle);
      server.setExecutor(Executors.newFixedThreadPool(4));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("스텁 백엔드 기동 실패", e);
    }
  }

  private static void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    LAST_REQUEST.put("method", exchange.getRequestMethod());
    LAST_REQUEST.put("path", path);
    LAST_REQUEST.put("query", String.valueOf(exchange.getRequestURI().getQuery()));
    exchange.getRequestHeaders().forEach((name, values) ->
        LAST_REQUEST.put(name.toLowerCase(), values.get(0)));
    try (InputStream in = exchange.getRequestBody()) {
      LAST_REQUEST.put("body", new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    int status = 200;
    String body = "{\"backend\":\"ok\"}";
    if (path.equals("/slow")) {
      sleep(2000);
    } else if (path.equals("/status/401")) {
      status = 401;
      body = "{\"httpStatus\":401,\"messageId\":\"C0009\"}";
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static int findClosedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
