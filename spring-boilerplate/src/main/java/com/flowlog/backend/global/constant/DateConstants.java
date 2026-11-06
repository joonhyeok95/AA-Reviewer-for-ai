package com.flowlog.backend.global.constant;

import java.time.LocalDate;

/**
 * 날짜 관련 상수.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class DateConstants {

  /** 무기한 종료일. (유효기간 END_DT 기본값) */
  public static final LocalDate MAX_END_DATE = LocalDate.of(9999, 12, 31);

  private DateConstants() {
    throw new UnsupportedOperationException("상수 클래스는 인스턴스화할 수 없습니다.");
  }
}
