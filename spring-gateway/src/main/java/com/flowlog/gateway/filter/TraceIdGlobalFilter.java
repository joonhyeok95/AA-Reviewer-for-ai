package com.flowlog.gateway.filter;

import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 요청 추적 ID(X-Trace-Id) 전파 + 접근 로그.
 *
 * <ul>
 *   <li>요청에 유효한 X-Trace-Id 가 있으면 재사용, 없으면 생성해 백엔드로 전달한다.
 *       (spring-boilerplate 의 TraceIdFilter 가 같은 값을 MDC 에 사용 → 로그 연계)</li>
 *   <li>응답 헤더에도 X-Trace-Id 를 넣는다.</li>
 *   <li>라우팅 결과(라우트ID, 상태코드, 소요시간)를 INFO 로 기록한다.</li>
 * </ul>
 *
 * <p>Netty 이벤트 루프에서 동작하므로 블로킹 호출을 하지 않는다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

  /** 요청 추적 ID Header. */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /** 외부 입력 traceId 허용 형식 (로그 인젝션 방지). */
  private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId = resolveTraceId(exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER));
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> headers.set(TRACE_ID_HEADER, traceId))
        .build();
    exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
    long start = System.nanoTime();

    return chain.filter(exchange.mutate().request(request).build())
        .doFinally(signal -> logAccess(exchange, traceId, start));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private void logAccess(ServerWebExchange exchange, String traceId, long start) {
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    String routeId = route == null ? "-" : route.getId();
    log.info("[{}] {} {} -> route={} status={} ({} ms)",
        traceId,
        exchange.getRequest().getMethod(),
        exchange.getRequest().getURI().getRawPath(),
        routeId,
        exchange.getResponse().getStatusCode(),
        (System.nanoTime() - start) / 1_000_000);
  }

  private String resolveTraceId(String headerValue) {
    String traceId = UUID.randomUUID().toString().replace("-", "");
    if (headerValue != null && VALID_TRACE_ID.matcher(headerValue).matches()) {
      traceId = headerValue;
    }
    return traceId;
  }
}
