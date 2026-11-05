package com.flowlog.backend.code.domain.service.dto;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * 사용 중인 공통코드 (캐시 단위).
 *
 * @param groupCd 그룹코드
 * @param code 코드
 * @param sortOrder 정렬순서
 * @param attr1 확장속성1 (LANG: 에디터 언어팩)
 * @param attr2 확장속성2
 * @param attr3 확장속성3
 * @param names 언어별 코드명 (언어코드 → 코드명)
 * @author flowlog
 * @since 2026-11-05
 */
public record CodeItem(
    String groupCd, String code, Integer sortOrder, String attr1, String attr2, String attr3,
    Map<String, String> names) {

  /**
   * 언어별 코드명. 해당 언어 이름이 없으면 대체 언어, 그래도 없으면 코드값.
   *
   * @param langCd 언어코드
   * @param fallbackLangCd 대체 언어코드 (기본 언어)
   * @return 코드명
   */
  public String nameOf(String langCd, String fallbackLangCd) {
    String name = names.get(langCd);
    if (StringUtils.isBlank(name)) {
      name = StringUtils.defaultIfBlank(names.get(fallbackLangCd), code);
    }
    return name;
  }
}
