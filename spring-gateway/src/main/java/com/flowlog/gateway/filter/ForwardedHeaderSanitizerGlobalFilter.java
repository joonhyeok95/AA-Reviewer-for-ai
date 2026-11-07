package com.flowlog.gateway.filter;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.headers.TrustedProxies;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 클라이언트가 보낸 Forwarded / X-Forwarded-* 헤더 정리.
 *
 * <p>백엔드(spring-boilerplate)는 이 헤더로 외부 URL(redirect, SAML ACS, Swagger)을 만든다.
 * 클라이언트가 위조한 값이 백엔드까지 가면 Host/Prefix 를 조작할 수 있으므로:
 * <ul>
 *   <li>{@code X-Forwarded-Prefix} 는 항상 제거한다. (prefix 는 Gateway 라우트가 정하는 값.
 *       Gateway 는 자기 값을 다시 추가한다)</li>
 *   <li>{@code Forwarded}(RFC 7239) 도 항상 제거한다. Gateway 는 이 헤더를 보내지 않고(forwarded.enabled=false)
 *       X-Forwarded-* 만 쓴다. 백엔드는 Forwarded 를 우선하므로 LB(ALB 는 임의 헤더를 그대로 전달)를 거친
 *       클라이언트 값이 남으면 host/proto 를 위조할 수 있다</li>
 *   <li>신뢰 프록시({@code trusted-proxies}, 예: 앞단 LB)가 아닌 곳에서 온 요청은
 *       {@code Forwarded} 와 모든 {@code X-Forwarded-*} 를 제거한다.</li>
 * </ul>
 * 신뢰 프록시가 보낸 Proto/Port/For 는 유지한다. (TLS 종료 LB 의 https 정보)
 * Host/Prefix 는 {@code x-forwarded.host-append / prefix-append: false} 로 Gateway 값으로 덮어쓴다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
public class ForwardedHeaderSanitizerGlobalFilter implements GlobalFilter, Ordered {

  /** 제거 대상 (클라이언트 위조 가능 헤더). */
  private static final List<String> FORWARDED_HEADERS = List.of(
      "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Port",
      "X-Forwarded-Proto", "X-Forwarded-Prefix", "X-Forwarded-Server", "X-Forwarded-Ssl",
      "X-Real-IP");

  /** 출처와 무관하게 항상 제거 (Gateway 소유 Prefix, 쓰지 않는 RFC 7239 Forwarded). */
  private static final List<String> ALWAYS_REMOVED = List.of("X-Forwarded-Prefix", "Forwarded");

  private final TrustedProxies trustedProxies;

  /**
   * 생성자.
   *
   * @param trustedProxies 신뢰 프록시 IP 정규식 (Gateway 의 trusted-proxies 와 동일 값)
   */
  public ForwardedHeaderSanitizerGlobalFilter(
      @Value("${spring.cloud.gateway.server.webflux.trusted-proxies:}") String trustedProxies) {
    this.trustedProxies =
        StringUtils.hasText(trustedProxies) ? TrustedProxies.from(trustedProxies) : null;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    HttpHeaders incoming = exchange.getRequest().getHeaders();
    boolean trusted = isTrusted(exchange.getRequest().getRemoteAddress());
    List<String> targets = trusted ? ALWAYS_REMOVED : FORWARDED_HEADERS;
    ServerWebExchange result = exchange;
    if (targets.stream().anyMatch(incoming::containsHeader)) {
      if (log.isDebugEnabled()) {
        log.debug("[Forwarded] 클라이언트 헤더 제거 trusted={} headers={}", trusted,
            targets.stream().filter(incoming::containsHeader)
                .map(h -> h.toLowerCase(Locale.ROOT)).toList());
      }
      ServerHttpRequest request = exchange.getRequest().mutate()
          .headers(headers -> targets.forEach(headers::remove))
          .build();
      result = exchange.mutate().request(request).build();
    }
    return chain.filter(result);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  private boolean isTrusted(InetSocketAddress remote) {
    return trustedProxies != null && remote != null && remote.getAddress() != null
        && trustedProxies.isTrusted(remote.getAddress().getHostAddress());
  }
}
