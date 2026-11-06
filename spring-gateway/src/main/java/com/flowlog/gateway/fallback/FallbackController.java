package com.flowlog.gateway.fallback;

import com.flowlog.gateway.exception.GatewayErrorCode;
import com.flowlog.gateway.exception.GatewayErrorHandler;
import com.flowlog.gateway.exception.GatewayResponse;
import com.flowlog.gateway.exception.GatewayResponseWriter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 서킷브레이커 fallback. ({@code fallbackUri: forward:/fallback/{routeId}})
 *
 * <table>
 *   <caption>fallback 응답</caption>
 *   <tr><th>원인</th><th>Status</th><th>messageId</th></tr>
 *   <tr><td>서킷 OPEN (호출 차단)</td><td>503</td><td>G0004</td></tr>
 *   <tr><td>연결 실패 / 백엔드 5xx</td><td>503</td><td>G0002</td></tr>
 *   <tr><td>시간 초과 (TimeLimiter)</td><td>504</td><td>G0003</td></tr>
 * </table>
 *
 * <p>서킷이 OPEN 이면 백엔드를 호출하지 않고 즉시 응답하므로, 장애 서비스로 요청이 몰려
 * Gateway 자원(커넥션/스레드)이 고갈되는 것을 막는다. 일정 시간 후 HALF_OPEN 으로 전환되어
 * 일부 요청으로 복구 여부를 확인한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FallbackController {

  private final GatewayResponseWriter responseWriter;

  /**
   * 서킷브레이커 fallback 응답.
   *
   * @param routeId 라우트 ID
   * @param exchange 현재 요청
   * @return 표준 응답
   */
  @RequestMapping("/fallback/{routeId}")
  public ResponseEntity<GatewayResponse> fallback(
      @PathVariable("routeId") String routeId, ServerWebExchange exchange) {
    Throwable ex = exchange.getAttribute(
        ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
    GatewayErrorCode errorCode = resolve(ex);
    log.warn("[CircuitBreaker] route={} -> {} ({})", routeId, errorCode.getMessageId(),
        ex == null ? "direct-call" : ex.getClass().getSimpleName());
    return ResponseEntity.status(errorCode.getStatus())
        .body(responseWriter.body(exchange, errorCode));
  }

  private GatewayErrorCode resolve(Throwable ex) {
    GatewayErrorCode errorCode;
    if (ex == null) {
      // 서킷브레이커를 거치지 않은 직접 호출 (/fallback/** 직접 접근)
      errorCode = GatewayErrorCode.NO_ROUTE;
    } else if (ex instanceof CallNotPermittedException) {
      errorCode = GatewayErrorCode.CIRCUIT_OPEN;
    } else if (GatewayErrorHandler.resolve(ex) == GatewayErrorCode.GATEWAY_TIMEOUT) {
      errorCode = GatewayErrorCode.GATEWAY_TIMEOUT;
    } else {
      errorCode = GatewayErrorCode.SERVICE_UNAVAILABLE;
    }
    return errorCode;
  }
}
