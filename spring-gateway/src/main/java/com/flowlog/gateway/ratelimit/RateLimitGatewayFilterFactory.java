package com.flowlog.gateway.ratelimit;

import com.flowlog.gateway.exception.GatewayErrorCode;
import com.flowlog.gateway.exception.GatewayResponseWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Rate Limit 라우트 필터. ({@code filters: - RateLimit})
 *
 * <p>기본 RequestRateLimiter 필터와 동일하게 {@link RedisRateLimiter}(Lua 스크립트)로 판단하되,
 * 한도 초과 시 빈 429 대신 표준 응답(429, G0005)을 반환한다.
 * 응답 헤더: {@code X-RateLimit-Remaining}, {@code X-RateLimit-Burst-Capacity},
 * {@code X-RateLimit-Replenish-Rate}, {@code X-RateLimit-Requested-Tokens}
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
public class RateLimitGatewayFilterFactory
    extends AbstractGatewayFilterFactory<RateLimitGatewayFilterFactory.Config> {

  /** 거부 건수 메트릭 (Prometheus: gateway_ratelimit_rejected_total{route}). */
  public static final String REJECTED_METRIC = "gateway.ratelimit.rejected";

  private final RedisRateLimiter rateLimiter;
  private final KeyResolver keyResolver;
  private final GatewayResponseWriter responseWriter;
  private final MeterRegistry meterRegistry;

  /**
   * 생성자.
   *
   * @param rateLimiter Redis Rate Limiter
   * @param keyResolver 키 결정 (세션/IP)
   * @param responseWriter 표준 응답 출력
   * @param meterRegistry 메트릭 레지스트리
   */
  public RateLimitGatewayFilterFactory(
      RedisRateLimiter rateLimiter, KeyResolver keyResolver,
      GatewayResponseWriter responseWriter, MeterRegistry meterRegistry) {
    super(Config.class);
    this.rateLimiter = rateLimiter;
    this.keyResolver = keyResolver;
    this.responseWriter = responseWriter;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> keyResolver.resolve(exchange)
        .defaultIfEmpty("unknown")
        .flatMap(key -> {
          Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
          String routeId = route == null ? "default" : route.getId();
          return rateLimiter.isAllowed(routeId, key).flatMap(response -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            response.getHeaders().forEach(headers::set);
            if (response.isAllowed()) {
              return chain.filter(exchange);
            }
            Counter.builder(REJECTED_METRIC)
                .description("Rate Limit 으로 거부된 요청 수 (429)")
                .tag("route", routeId)
                .register(meterRegistry)
                .increment();
            log.warn("[RateLimit] route={} key={} {} {}", routeId, key,
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath());
            return responseWriter.write(exchange, GatewayErrorCode.TOO_MANY_REQUESTS);
          });
        });
  }

  /** 필터 설정 (현재 전역 설정 app.gateway.rate-limit 사용). */
  public static class Config {
  }
}
