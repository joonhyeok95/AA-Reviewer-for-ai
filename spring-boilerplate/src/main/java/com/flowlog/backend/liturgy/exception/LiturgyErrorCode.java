package com.flowlog.backend.liturgy.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전례문 도메인 오류 코드. (할당 범위: B9501 ~ B9999)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@RequiredArgsConstructor
public enum LiturgyErrorCode implements ErrorCode {

  /** 전례문 없음. */
  TEXT_NOT_FOUND(HttpStatus.NOT_FOUND, "B9501"),
  /** 코드값 오류 (유형·화자). */
  CODE_INVALID(HttpStatus.BAD_REQUEST, "B9502"),
  /** 주보에서 사용 중. */
  TEXT_IN_USE(HttpStatus.CONFLICT, "B9503");

  private final HttpStatus status;
  private final String messageId;
}
