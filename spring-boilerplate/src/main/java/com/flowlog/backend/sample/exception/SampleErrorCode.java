package com.flowlog.backend.sample.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 샘플 도메인 오류 코드. (할당 범위: B1001 ~ B1999)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@RequiredArgsConstructor
public enum SampleErrorCode implements ErrorCode {

  SAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "B1001"),
  SAMPLE_NAME_DUPLICATED(HttpStatus.CONFLICT, "B1002");

  private final HttpStatus status;
  private final String messageId;
}
