package com.flowlog.backend.global.util;

import org.apache.commons.lang3.StringUtils;

/**
 * LIKE 검색 패턴.
 *
 * <p>사용자 입력의 와일드카드({@code %}, {@code _})와 이스케이프 문자({@code \})를 이스케이프해 문자 그대로
 * 검색되게 한다. 쿼리에는 반드시 {@code ESCAPE '\'} 를 명시한다.
 *
 * <pre>
 * JPQL    : s.name like :name escape '\'
 * MyBatis : I.TITLE LIKE #{keyword} ESCAPE '\'
 * </pre>
 *
 * <p>ESCAPE 를 생략하면 Hibernate H2 Dialect 가 {@code escape ''} 를 붙이는데, H2 Oracle 모드는 빈 문자열을
 * NULL 로 취급해 LIKE 결과가 항상 거짓이 된다. (local 에서 검색 결과 0건)
 *
 * @author flowlog
 * @since 2026-11-06
 */
public final class LikePatterns {

  /** 쿼리의 ESCAPE 문자. */
  public static final char ESCAPE = '\\';

  private LikePatterns() {
  }

  /**
   * 부분 일치 패턴 ({@code %값%}). 공백이면 null (조건 무시).
   *
   * @param value 사용자 입력
   * @return LIKE 패턴 또는 null
   */
  public static String contains(String value) {
    String trimmed = StringUtils.trimToNull(value);
    return trimmed == null ? null : "%" + escape(trimmed) + "%";
  }

  /**
   * 와일드카드 이스케이프.
   *
   * @param value 값
   * @return 이스케이프한 값
   */
  public static String escape(String value) {
    String escapeChar = String.valueOf(ESCAPE);
    return value.replace(escapeChar, escapeChar + escapeChar)
        .replace("%", escapeChar + "%")
        .replace("_", escapeChar + "_");
  }
}
