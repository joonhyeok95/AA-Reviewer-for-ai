package com.flowlog.backend.global.constant;

/**
 * 여부 컬럼(_YN) 값 상수.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class YesNo {

  /** 예. */
  public static final String Y = "Y";

  /** 아니오. */
  public static final String N = "N";

  private YesNo() {
    throw new UnsupportedOperationException("상수 클래스는 인스턴스화할 수 없습니다.");
  }

  /**
   * 'Y' 여부를 반환한다. (null-safe)
   *
   * @param value 여부 값
   * @return 'Y' 이면 true
   */
  public static boolean isYes(String value) {
    return Y.equals(value);
  }
}
