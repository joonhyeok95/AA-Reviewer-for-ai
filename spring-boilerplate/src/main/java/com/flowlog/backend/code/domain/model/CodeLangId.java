package com.flowlog.backend.code.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 다국어 PK. (GROUP_CD + CODE + LANG_CD)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeLangId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "GROUP_CD", length = 50)
  private String groupCd; // 그룹코드

  @Column(name = "CODE", length = 50)
  private String code; // 코드

  @Column(name = "LANG_CD", length = 10)
  private String langCd; // 언어코드
}
