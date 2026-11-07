package com.flowlog.backend.calendar.exception;

import com.flowlog.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 캘린더 도메인 오류 코드. (할당 범위: B7001 ~ B7999)
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements ErrorCode {

  /** 일정 없음. */
  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "B7001"),
  /** 일정 유형 코드 오류. */
  EVENT_TYPE_INVALID(HttpStatus.BAD_REQUEST, "B7002"),
  /** 종료일이 시작일보다 빠름. */
  EVENT_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "B7003"),
  /** 일정 기간 초과. */
  EVENT_PERIOD_TOO_LONG(HttpStatus.BAD_REQUEST, "B7004"),
  /** 조회 기간 오류. */
  RANGE_INVALID(HttpStatus.BAD_REQUEST, "B7005");

  private final HttpStatus status;
  private final String messageId;
}
