package com.flowlog.backend.code.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 다국어. (TB_CODE_LANG)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_CODE_LANG")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeLang extends AssignedIdEntity<CodeLangId> {

  @EmbeddedId
  private CodeLangId id; // 그룹코드 + 코드 + 언어코드

  @Column(name = "CODE_NM", length = 200, nullable = false)
  private String codeNm; // 코드명

  /**
   * 코드 다국어를 생성한다.
   *
   * @param groupCd 그룹코드
   * @param code 코드
   * @param langCd 언어코드
   * @param codeNm 코드명
   */
  public CodeLang(String groupCd, String code, String langCd, String codeNm) {
    this.id = new CodeLangId(groupCd, code, langCd);
    this.codeNm = codeNm;
  }

  /**
   * 코드명을 변경한다.
   *
   * @param codeNm 코드명
   */
  public void changeName(String codeNm) {
    this.codeNm = codeNm;
  }
}
