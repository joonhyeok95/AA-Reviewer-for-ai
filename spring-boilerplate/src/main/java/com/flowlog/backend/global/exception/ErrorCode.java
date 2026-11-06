package com.flowlog.backend.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 규약.
 *
 * <p>공통 오류는 {@link CommonErrorCode}, 업무 도메인 오류는 각 도메인의
 * {@code XxxErrorCode} enum 이 이 인터페이스를 구현하여 정의한다.
 * 메시지 본문은 {@code messages_*.properties} 에 {@code {messageId}.title/.content} 로 등록한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface ErrorCode {

  /**
   * 응답 HTTP 상태 코드.
   *
   * @return HTTP 상태
   */
  HttpStatus getStatus();

  /**
   * 메시지 ID.
   *
   * @return 메시지 ID
   */
  String getMessageId();
}
