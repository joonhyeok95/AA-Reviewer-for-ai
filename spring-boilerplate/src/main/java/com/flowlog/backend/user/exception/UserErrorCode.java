package com.flowlog.backend.user.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 사용자/언어 설정 오류 코드. (B3001 ~ B3999)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

  /** 등록되지 않은 사용자 (TB_USER 미연결 - 서비스 계정 등). */
  USER_NOT_REGISTERED(HttpStatus.NOT_FOUND, "B3001"),
  /** 사용하지 않는 언어. */
  INVALID_LANG(HttpStatus.BAD_REQUEST, "B3002");

  private final HttpStatus status;
  private final String messageId;
}
