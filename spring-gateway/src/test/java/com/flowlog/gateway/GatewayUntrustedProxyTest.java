package com.flowlog.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 신뢰 프록시가 아닌 클라이언트가 보낸 Forwarded / X-Forwarded-* 처리.
 *
 * <p>trusted-proxies 를 테스트 클라이언트(127.0.0.1)와 다른 대역으로 두고, 위조 헤더가 백엔드에
 * 전달되지 않는지(= 백엔드가 받는 값은 Gateway 가 만든 단일 값뿐인지) 확인한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainerConfig.class)
class GatewayUntrustedProxyTest {

  private static final Map<String, String> LAST_REQUEST = new ConcurrentHashMap<>();
  private static final HttpServer BACKEND = startBackend();

  @LocalServerPort
  private int gatewayPort;

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    String p = "spring.cloud.gateway.server.webflux.";
    registry.add(p + "routes[0].id", () -> "cm");
    registry.add(p + "routes[0].uri", () -> "http://localhost:" + BACKEND.getAddress().getPort());
    registry.add(p + "routes[0].predicates[0]", () -> "Path=/cm/**");
    registry.add(p + "routes[0].filters[0]", () -> "StripPrefix=1");
    registry.add(p + "trusted-proxies", () -> "10\\.255\\.255\\.1");
  }

  @AfterAll
  static void tearDown() {
    BACKEND.stop(0);
  }

  @Test
  @DisplayName("비신뢰 클라이언트의 위조 X-Forwarded-*/Forwarded/X-Real-IP 는 제거 → 백엔드는 Gateway 값만 받음")
  void spoofedHeadersRemoved() {
    WebTestClient.bindToServer().baseUrl("http://localhost:" + gatewayPort).build()
        .get().uri("/cm/api/v1/samples")
        .header("X-Forwarded-Host", "evil.example")
        .header("X-Forwarded-Prefix", "/evil")
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Port", "443")
        .header("X-Forwarded-For", "6.6.6.6")
        .header("X-Real-IP", "6.6.6.6")
        .header("Forwarded", "host=evil.example;proto=https;for=6.6.6.6")
        .exchange().expectStatus().isOk();

    assertThat(LAST_REQUEST.get("x-forwarded-host")).isEqualTo("localhost:" + gatewayPort);
    assertThat(LAST_REQUEST.get("x-forwarded-prefix")).isEqualTo("/cm");
    assertThat(LAST_REQUEST.get("x-forwarded-proto")).isEqualTo("http");
    assertThat(LAST_REQUEST.get("x-forwarded-port")).isEqualTo(String.valueOf(gatewayPort));
    assertThat(LAST_REQUEST.get("x-forwarded-for")).doesNotContain("6.6.6.6");
    assertThat(LAST_REQUEST).doesNotContainKey("x-real-ip");
    assertThat(String.valueOf(LAST_REQUEST.get("forwarded")))
        .doesNotContain("evil").doesNotContain("6.6.6.6");
  }

  private static HttpServer startBackend() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> {
        LAST_REQUEST.clear();
        exchange.getRequestHeaders().forEach((name, values) ->
            LAST_REQUEST.put(name.toLowerCase(), String.join(",", values)));
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
