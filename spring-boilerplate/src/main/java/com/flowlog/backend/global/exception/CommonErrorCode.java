package com.flowlog.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 오류 코드.
 *
 * <ul>
 *   <li>C0xxx : 공통 요청 오류</li>
 *   <li>B0xxx : 공통 비즈니스 오류 (도메인 전용 오류는 B1xxx~ 를 도메인별로 할당)</li>
 *   <li>E0xxx : 시스템 오류</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

  BAD_REQUEST(HttpStatus.BAD_REQUEST, "C0001"),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "C0002"),
  INVALID_TYPE(HttpStatus.BAD_REQUEST, "C0003"),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "C0004"),
  UNREADABLE_BODY(HttpStatus.BAD_REQUEST, "C0005"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C0006"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "C0007"),
  PATH_NOT_FOUND(HttpStatus.NOT_FOUND, "C0008"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C0009"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "C0010"),

  DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "B0001"),
  DUPLICATE_DATA(HttpStatus.CONFLICT, "B0002"),
  DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "B0003"),

  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "E0001");

  private final HttpStatus status;
  private final String messageId;
}
