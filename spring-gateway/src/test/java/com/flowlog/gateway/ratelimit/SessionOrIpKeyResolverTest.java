package com.flowlog.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link SessionOrIpKeyResolver} 단위 테스트. (세션 존재 확인 / 클라이언트 IP 결정)
 *
 * @author flowlog
 * @since 2026-11-05
 */
class SessionOrIpKeyResolverTest {

  private static final String PREFIX = "flowlog:session:sessions:";
  private static final String SESSION_ID = "6b1f3c2a-1111-2222-3333-444455556666";
  private static final InetSocketAddress LB = new InetSocketAddress("10.0.0.5", 50000);

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);

  @Test
  @DisplayName("hops=0 : X-Forwarded-For 를 무시하고 TCP 접속 IP 사용 (위조 XFF 로 버킷 분산 불가)")
  void ip_directConnection() {
    MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/cm/x")
        .remoteAddress(LB).header("X-Forwarded-For", "6.6.6.6"));

    assertThat(resolve(0, exchange)).isEqualTo("ip:10.0.0.5");
  }

  @Test
  @DisplayName("hops=1 : LB 뒤 - X-Forwarded-For 의 마지막(LB 가 추가한) 값 사용, 앞쪽 위조 값 무시")
  void ip_behindLoadBalancer() {
    MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/cm/x")
        .remoteAddress(LB).header("X-Forwarded-For", "6.6.6.6, 203.0.113.7"));

    assertThat(resolve(1, exchange)).isEqualTo("ip:203.0.113.7");
  }

  @Test
  @DisplayName("hops=1 이지만 X-Forwarded-For 없음 - TCP 접속 IP")
  void ip_behindLoadBalancer_noHeader() {
    MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/cm/x").remoteAddress(LB));

    assertThat(resolve(1, exchange)).isEqualTo("ip:10.0.0.5");
  }

  @Test
  @DisplayName("Redis 에 존재하는 세션 - session:{해시} 키 (세션ID 원문 미포함)")
  void session_exists() {
    when(redis.hasKey(PREFIX + SESSION_ID)).thenReturn(Mono.just(true));

    String key = resolve(0, withCookie(encode(SESSION_ID)));

    assertThat(key).startsWith("session:").hasSize("session:".length() + 32)
        .doesNotContain(SESSION_ID);
  }

  @Test
  @DisplayName("위조/만료 세션(Redis 에 없음) - IP 키")
  void session_notExists() {
    when(redis.hasKey(anyString())).thenReturn(Mono.just(false));

    assertThat(resolve(0, withCookie(encode(SESSION_ID)))).isEqualTo("ip:10.0.0.5");
  }

  @Test
  @DisplayName("Base64 가 아니거나 세션ID 형식이 아닌 쿠키 - Redis 조회 없이 IP 키")
  void session_invalidCookie() {
    assertThat(resolve(0, withCookie("not base64 !!"))).isEqualTo("ip:10.0.0.5");
    assertThat(resolve(0, withCookie(encode("a*b:c\r\nFLUSHALL")))).isEqualTo("ip:10.0.0.5");
    verify(redis, never()).hasKey(anyString());
  }

  @Test
  @DisplayName("Redis 오류 - IP 키로 계속 처리")
  void session_redisError() {
    when(redis.hasKey(anyString()))
        .thenReturn(Mono.error(new RedisConnectionFailureException("down")));

    assertThat(resolve(0, withCookie(encode(SESSION_ID)))).isEqualTo("ip:10.0.0.5");
  }

  @Test
  @DisplayName("Spring Session 쿠키 디코딩 - 패딩 없는 Base64 도 허용")
  void sessionId_decoding() {
    String unpadded = encode(SESSION_ID).replace("=", "");

    assertThat(SessionOrIpKeyResolver.sessionId(new HttpCookie("SESSION", unpadded)))
        .isEqualTo(SESSION_ID);
  }

  private String resolve(int hops, MockServerWebExchange exchange) {
    return new SessionOrIpKeyResolver(redis, PREFIX, hops).resolve(exchange).block();
  }

  private static MockServerWebExchange withCookie(String value) {
    return exchange(MockServerHttpRequest.get("/cm/x").remoteAddress(LB)
        .cookie(new HttpCookie("SESSION", value)));
  }

  private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
    return MockServerWebExchange.from(request);
  }

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
