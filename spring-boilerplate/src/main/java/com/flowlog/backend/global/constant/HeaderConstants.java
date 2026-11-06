package com.flowlog.backend.global.constant;

/**
 * HTTP Header / MDC Key 상수.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class HeaderConstants {

  /** 요청 추적 ID Header (요청에 있으면 재사용, 없으면 생성). */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /** 로그 MDC Key. */
  public static final String TRACE_ID_MDC_KEY = "traceId";

  private HeaderConstants() {
    throw new UnsupportedOperationException("상수 클래스는 인스턴스화할 수 없습니다.");
  }
}
