package com.flowlog.gateway.ratelimit;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate Limit 키 결정 : {@code session:{세션ID 해시}} 또는 {@code ip:{클라이언트 IP}}.
 *
 * <ul>
 *   <li>SESSION 쿠키(Spring Session, Base64 인코딩된 세션ID)를 디코딩해 Redis 에 세션이
 *       <b>실제로 존재할 때만</b> 세션 단위 버킷을 쓴다. 위조/만료 쿠키는 IP 버킷으로 처리하므로
 *       쿠키 값을 바꿔 가며 한도를 우회하거나 Redis 키를 무한히 만드는 공격이 막힌다.</li>
 *   <li>세션ID 원문이 Redis 키에 남지 않도록 SHA-256 해시(앞 16바이트)를 사용한다.</li>
 *   <li>Redis 조회 실패 시 IP 버킷으로 처리한다. (논블로킹 EXISTS)</li>
 *   <li>클라이언트 IP : {@code trustedProxyHops} 가 0 이면 TCP 접속 IP, 1 이상이면
 *       X-Forwarded-For 의 오른쪽에서 n 번째 값 (신뢰 프록시가 추가한 값만 사용).</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
public class SessionOrIpKeyResolver implements KeyResolver {

  /** spring-boilerplate(Spring Session) 세션 쿠키 이름. */
  public static final String SESSION_COOKIE = "SESSION";

  /** 세션ID 허용 형식 (Spring Session 은 UUID). Redis 키 주입 방지. */
  private static final Pattern VALID_SESSION_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

  private final ReactiveStringRedisTemplate redisTemplate;
  private final String sessionKeyPrefix;
  private final RemoteAddressResolver addressResolver;

  /**
   * 생성자.
   *
   * @param redisTemplate Redis (세션 존재 확인)
   * @param sessionKeyPrefix 세션 키 접두사 (예: flowlog:session:sessions:)
   * @param trustedProxyHops 신뢰 프록시 단계 수 (0 = TCP 접속 IP)
   */
  public SessionOrIpKeyResolver(
      ReactiveStringRedisTemplate redisTemplate, String sessionKeyPrefix, int trustedProxyHops) {
    this.redisTemplate = redisTemplate;
    this.sessionKeyPrefix = sessionKeyPrefix;
    this.addressResolver = trustedProxyHops > 0
        ? XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxyHops)
        : new RemoteAddressResolver() { };
  }

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    String ipKey = "ip:" + clientIp(exchange);
    String sessionId = sessionId(exchange.getRequest().getCookies().getFirst(SESSION_COOKIE));
    Mono<String> key = Mono.just(ipKey);
    if (sessionId != null) {
      key = redisTemplate.hasKey(sessionKeyPrefix + sessionId)
          .map(exists -> Boolean.TRUE.equals(exists) ? "session:" + sha256(sessionId) : ipKey)
          .onErrorResume(e -> {
            log.warn("[RateLimit] 세션 확인 실패 → IP 키 사용 ({})", e.getClass().getSimpleName());
            return Mono.just(ipKey);
          })
          .defaultIfEmpty(ipKey);
    }
    return key;
  }

  private String clientIp(ServerWebExchange exchange) {
    InetSocketAddress remote = addressResolver.resolve(exchange);
    String ip = "unknown";
    if (remote != null) {
      ip = remote.getAddress() != null
          ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
    return ip;
  }

  /**
   * SESSION 쿠키 → 세션ID. (Base64 디코딩 실패/형식 불일치는 세션 없음)
   *
   * @param cookie SESSION 쿠키
   * @return 세션ID, 유효하지 않으면 null
   */
  static String sessionId(HttpCookie cookie) {
    String id = null;
    if (cookie != null && !cookie.getValue().isBlank()) {
      try {
        String decoded = new String(Base64.getDecoder().decode(pad(cookie.getValue().trim())),
            StandardCharsets.UTF_8);
        if (VALID_SESSION_ID.matcher(decoded).matches()) {
          id = decoded;
        }
      } catch (IllegalArgumentException e) {
        id = null;
      }
    }
    return id;
  }

  private static String pad(String value) {
    int remainder = value.length() % 4;
    return remainder == 0 ? value : value + "=".repeat(4 - remainder);
  }

  private static String sha256(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
    }
  }
}
