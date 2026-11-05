package com.flowlog.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowlog.gateway.ratelimit.RateLimitGatewayFilterFactory;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Rate Limit(Redis Lua) / 서킷브레이커(Resilience4j) 통합 테스트.
 *
 * <p>실제 Gateway + 실제 Redis(Testcontainers) + 스텁 백엔드로 검증한다.
 * 테스트 시간을 줄이기 위해 한도/임계치를 작게 둔다.
 *
 * <ul>
 *   <li>rl    : /rl/**    → 스텁 (RateLimit: 초당 1개 충전, 최대 3개)</li>
 *   <li>dead  : /dead/**  → 닫힌 포트 (CircuitBreaker "dead": 최소 3건, 실패율 50%)</li>
 *   <li>flaky : /flaky/** → 스텁 (장애 스위치, CircuitBreaker "flaky": OPEN 1초 후 HALF_OPEN)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class GatewayResilienceTest {

  private static final AtomicBoolean FLAKY_FAILING = new AtomicBoolean(false);
  private static final AtomicInteger FLAKY_CALLS = new AtomicInteger();
  private static final HttpServer BACKEND = startBackend();
  private static final int CLOSED_PORT = findClosedPort();

  @LocalServerPort
  private int gatewayPort;

  private static final String SESSION_KEY_PREFIX = "flowlog:session:sessions:";

  @Autowired
  private ReactiveStringRedisTemplate redisTemplate;

  @Autowired
  private MeterRegistry meterRegistry;

  private WebTestClient client;

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    String backend = "http://localhost:" + BACKEND.getAddress().getPort();
    String p = "spring.cloud.gateway.server.webflux.routes";
    route(registry, p, 0, "rl", backend, "/rl/**", List.of("StripPrefix=1", "RateLimit"));
    route(registry, p, 1, "dead", "http://localhost:" + CLOSED_PORT, "/dead/**",
        List.of("StripPrefix=1"));
    circuitBreaker(registry, p, 1, "dead");
    route(registry, p, 2, "flaky", backend, "/flaky/**", List.of("StripPrefix=1"));
    circuitBreaker(registry, p, 2, "flaky");
    // flaky 는 백엔드 503 응답도 실패로 집계
    registry.add(p + "[2].filters[1].args.statusCodes[0]", () -> "SERVICE_UNAVAILABLE");

    registry.add("app.gateway.rate-limit.replenish-rate", () -> "1");
    registry.add("app.gateway.rate-limit.burst-capacity", () -> "3");
    for (String cb : List.of("dead", "flaky")) {
      String c = "resilience4j.circuitbreaker.instances." + cb + ".";
      registry.add(c + "sliding-window-size", () -> "4");
      registry.add(c + "minimum-number-of-calls", () -> "3");
      registry.add(c + "failure-rate-threshold", () -> "50");
      registry.add(c + "wait-duration-in-open-state", () -> "1s");
      registry.add(c + "permitted-number-of-calls-in-half-open-state", () -> "1");
      registry.add(c + "automatic-transition-from-open-to-half-open-enabled", () -> "true");
    }
  }

  private static void route(DynamicPropertyRegistry registry, String p, int i, String id,
      String uri, String path, List<String> filters) {
    registry.add(p + "[" + i + "].id", () -> id);
    registry.add(p + "[" + i + "].uri", () -> uri);
    registry.add(p + "[" + i + "].predicates[0]", () -> "Path=" + path);
    for (int f = 0; f < filters.size(); f++) {
      String filter = filters.get(f);
      registry.add(p + "[" + i + "].filters[" + f + "]", () -> filter);
    }
  }

  /** CircuitBreaker 필터를 운영 설정과 같은 args 형식으로 등록한다. (filters[1]) */
  private static void circuitBreaker(
      DynamicPropertyRegistry registry, String p, int i, String name) {
    String f = p + "[" + i + "].filters[1].";
    registry.add(f + "name", () -> "CircuitBreaker");
    registry.add(f + "args.name", () -> name);
    registry.add(f + "args.fallbackUri", () -> "forward:/fallback/" + name);
  }

  @BeforeEach
  void setUp() {
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
  @DisplayName("Rate Limit - 버킷(3) 소진 시 429 G0005 표준 응답, X-RateLimit 헤더, Redis 에 Lua 토큰 버킷 키, 거부 메트릭")
  void rateLimit_exceeded() {
    String sessionId = createSession();
    String cookie = sessionCookie(sessionId);
    double rejectedBefore = rejectedCount("rl");
    for (int i = 0; i < 3; i++) {
      client.get().uri("/rl/ping").header("Cookie", cookie).exchange()
          .expectStatus().isOk()
          .expectHeader().valueEquals("X-RateLimit-Remaining", String.valueOf(2 - i));
    }

    client.get().uri("/rl/ping").header("Cookie", cookie).exchange()
        .expectStatus().isEqualTo(429)
        .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
        .expectBody()
        .jsonPath("$.httpStatus").isEqualTo(429)
        .jsonPath("$.messageId").isEqualTo("G0005")
        .jsonPath("$.messageTitle").isEqualTo("요청 한도 초과")
        .jsonPath("$.path").isEqualTo("/rl/ping");

    List<String> keys = redisTemplate.keys("request_rate_limiter.{rl.session:*").collectList()
        .block(Duration.ofSeconds(5));
    assertThat(keys).anyMatch(k -> k.endsWith(".tokens")).anyMatch(k -> k.endsWith(".timestamp"))
        .noneMatch(k -> k.contains(sessionId)); // 세션ID 원문은 해시 처리
    assertThat(rejectedCount("rl")).isGreaterThan(rejectedBefore);
  }

  @Test
  @DisplayName("Rate Limit - 키는 세션 단위 : 다른 (실제) 세션은 별도 버킷이라 영향 없음")
  void rateLimit_perSession() {
    String busy = sessionCookie(createSession());
    for (int i = 0; i < 4; i++) {
      client.get().uri("/rl/ping").header("Cookie", busy).exchange();
    }
    client.get().uri("/rl/ping").header("Cookie", busy).exchange()
        .expectStatus().isEqualTo(429);

    client.get().uri("/rl/ping").header("Cookie", sessionCookie(createSession())).exchange()
        .expectStatus().isOk();
  }

  @Test
  @DisplayName("Rate Limit - 위조 SESSION 쿠키를 매번 바꿔도 IP 버킷 하나로 집계 → 429 (우회 불가, Redis 키 증가 없음)")
  void rateLimit_forgedSessionCookies() {
    int status = 200;
    for (int i = 0; i < 10 && status != 429; i++) {
      String forged = sessionCookie(UUID.randomUUID().toString()); // Redis 에 없는 세션
      status = client.get().uri("/rl/ping").header("Cookie", forged).exchange()
          .returnResult(String.class).getStatus().value();
    }
    assertThat(status).isEqualTo(429);

    List<String> keys = redisTemplate.keys("request_rate_limiter.{rl.*").collectList()
        .block(Duration.ofSeconds(5));
    assertThat(keys).anyMatch(k -> k.startsWith("request_rate_limiter.{rl.ip:"));
  }

  /** spring-boilerplate 가 로그인 시 만드는 것과 같은 키로 Redis 에 세션을 만든다. */
  private String createSession() {
    String sessionId = UUID.randomUUID().toString();
    redisTemplate.opsForHash()
        .put(SESSION_KEY_PREFIX + sessionId, "creationTime", "0")
        .block(Duration.ofSeconds(5));
    return sessionId;
  }

  /** Spring Session 쿠키 형식 (세션ID Base64). */
  private static String sessionCookie(String sessionId) {
    return "SESSION=" + Base64.getEncoder()
        .encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
  }

  private double rejectedCount(String route) {
    Counter counter = meterRegistry.find(RateLimitGatewayFilterFactory.REJECTED_METRIC)
        .tag("route", route).counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  @DisplayName("서킷브레이커 - 백엔드 중지 시 3건 실패(503 G0002) 후 OPEN → 호출 없이 즉시 503 G0004")
  void circuit_opensWhenBackendDown() {
    for (int i = 0; i < 3; i++) {
      client.get().uri("/dead/api").exchange()
          .expectStatus().isEqualTo(503)
          .expectBody().jsonPath("$.messageId").isEqualTo("G0002");
    }

    long start = System.nanoTime();
    client.get().uri("/dead/api").exchange()
        .expectStatus().isEqualTo(503)
        .expectBody()
        .jsonPath("$.messageId").isEqualTo("G0004")
        .jsonPath("$.messageTitle").isEqualTo("서비스 일시 중단")
        .jsonPath("$.path").isEqualTo("/dead/api");
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000);
  }

  @Test
  @DisplayName("서킷브레이커 - OPEN 동안 백엔드 호출 차단, 복구 후 HALF_OPEN 시험 호출 성공 → CLOSED")
  void circuit_recovers() throws InterruptedException {
    FLAKY_FAILING.set(true);
    for (int i = 0; i < 3; i++) {
      client.get().uri("/flaky/api").exchange().expectStatus().isEqualTo(503);
    }
    int callsWhenOpened = FLAKY_CALLS.get();

    EntityExchangeResult<byte[]> open = client.get().uri("/flaky/api").exchange()
        .expectStatus().isEqualTo(503).expectBody().returnResult();
    assertThat(new String(open.getResponseBodyContent(), StandardCharsets.UTF_8))
        .contains("G0004");
    assertThat(FLAKY_CALLS.get()).isEqualTo(callsWhenOpened); // OPEN 중 백엔드 미호출

    FLAKY_FAILING.set(false);
    Thread.sleep(1500); // wait-duration-in-open-state(1s) 경과 → HALF_OPEN

    client.get().uri("/flaky/api").exchange()
        .expectStatus().isOk()
        .expectBody().jsonPath("$.backend").isEqualTo("ok");
    client.get().uri("/flaky/api").exchange().expectStatus().isOk();
  }

  // ---------------------------------------------------------------- stub backend

  private static HttpServer startBackend() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> {
        int status = 200;
        String body = "{\"backend\":\"ok\"}";
        if (exchange.getRequestURI().getPath().startsWith("/api")) {
          FLAKY_CALLS.incrementAndGet();
          if (FLAKY_FAILING.get()) {
            status = 503;
            body = "{\"backend\":\"down\"}";
          }
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
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

  private static int findClosedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
