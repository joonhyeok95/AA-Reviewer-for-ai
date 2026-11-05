package com.flowlog.gateway.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Gateway 오류를 표준 응답 형식으로 반환한다. (Spring Boot 기본 에러 핸들러(order -1)보다 먼저 동작)
 *
 * <table>
 *   <caption>오류 매핑</caption>
 *   <tr><th>상황</th><th>Status</th><th>messageId</th></tr>
 *   <tr><td>매칭 라우트 없음</td><td>404</td><td>G0001</td></tr>
 *   <tr><td>백엔드 연결 실패</td><td>503</td><td>G0002</td></tr>
 *   <tr><td>백엔드 응답 시간 초과</td><td>504</td><td>G0003</td></tr>
 *   <tr><td>서킷 OPEN (fallback 미설정 라우트)</td><td>503</td><td>G0004</td></tr>
 *   <tr><td>기타</td><td>500</td><td>E0001</td></tr>
 * </table>
 *
 * <p>서킷브레이커가 적용된 라우트의 백엔드 장애는 {@code FallbackController} 가 처리한다.
 * 백엔드가 반환한 4xx/5xx 응답(예: 401 C0009)은 정상 프록시 응답이므로 그대로 전달된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorHandler implements WebExceptionHandler {

  private final GatewayResponseWriter responseWriter;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    Mono<Void> result;
    if (exchange.getResponse().isCommitted()) {
      result = Mono.error(ex);
    } else {
      GatewayErrorCode errorCode = resolve(ex);
      logError(exchange, errorCode, ex);
      result = responseWriter.write(exchange, errorCode);
    }
    return result;
  }

  /**
   * 예외를 오류 코드로 변환한다. (FallbackController 와 공용)
   *
   * @param ex 예외
   * @return 오류 코드
   */
  public static GatewayErrorCode resolve(Throwable ex) {
    GatewayErrorCode errorCode = GatewayErrorCode.INTERNAL_ERROR;
    if (hasCause(ex, CallNotPermittedException.class)) {
      errorCode = GatewayErrorCode.CIRCUIT_OPEN;
    } else if (hasCause(ex, ConnectException.class)) {
      errorCode = GatewayErrorCode.SERVICE_UNAVAILABLE;
    } else if (hasCause(ex, TimeoutException.class)) {
      errorCode = GatewayErrorCode.GATEWAY_TIMEOUT;
    } else if (ex instanceof ResponseStatusException statusException) {
      errorCode = fromStatus(statusException.getStatusCode());
    }
    return errorCode;
  }

  /**
   * 원인 체인에 특정 타입의 예외가 있는지 확인한다.
   *
   * @param ex 예외
   * @param type 찾을 타입
   * @return 존재 여부
   */
  public static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
    boolean found = false;
    Throwable current = ex;
    while (current != null && !found) {
      found = type.isInstance(current);
      current = current.getCause() == current ? null : current.getCause();
    }
    return found;
  }

  private static GatewayErrorCode fromStatus(HttpStatusCode status) {
    GatewayErrorCode errorCode;
    switch (status.value()) {
      case 404:
        errorCode = GatewayErrorCode.NO_ROUTE;
        break;
      case 429:
        errorCode = GatewayErrorCode.TOO_MANY_REQUESTS;
        break;
      case 502:
      case 503:
        errorCode = GatewayErrorCode.SERVICE_UNAVAILABLE;
        break;
      case 504:
        errorCode = GatewayErrorCode.GATEWAY_TIMEOUT;
        break;
      default:
        errorCode = GatewayErrorCode.INTERNAL_ERROR;
        break;
    }
    return errorCode;
  }

  private void logError(ServerWebExchange exchange, GatewayErrorCode errorCode, Throwable ex) {
    String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
    if (errorCode.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
      log.error("[{}] {} {} -> {} {}", traceId, exchange.getRequest().getMethod(),
          exchange.getRequest().getURI().getRawPath(), errorCode.getMessageId(),
          ex.getMessage(), ex);
    } else {
      log.warn("[{}] {} {} -> {} ({})", traceId, exchange.getRequest().getMethod(),
          exchange.getRequest().getURI().getRawPath(), errorCode.getMessageId(),
          ex.getClass().getSimpleName());
    }
  }
}
