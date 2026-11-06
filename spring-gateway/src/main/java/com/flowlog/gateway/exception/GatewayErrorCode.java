package com.flowlog.gateway.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Gateway 오류 코드. (메시지는 messages_*.properties)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@RequiredArgsConstructor
public enum GatewayErrorCode {

  /** 매칭되는 라우트 없음. */
  NO_ROUTE(HttpStatus.NOT_FOUND, "G0001"),

  /** 백엔드 이용 불가 (연결 실패, 5xx 응답). */
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "G0002"),

  /** 백엔드 응답 시간 초과. */
  GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "G0003"),

  /** 서킷 OPEN - 장애 서비스로의 호출을 차단 중. */
  CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "G0004"),

  /** 요청 한도 초과 (Rate Limit). */
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "G0005"),

  /** 허용하지 않는 요청 경로 형식 (경로 우회 시도: {@code ;}, {@code //}, {@code ..}, 인코딩된 구분자 등). */
  INVALID_PATH(HttpStatus.BAD_REQUEST, "G0006"),

  /** 그 외 Gateway 내부 오류. */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E0001");

  private final HttpStatus status;
  private final String messageId;
}
