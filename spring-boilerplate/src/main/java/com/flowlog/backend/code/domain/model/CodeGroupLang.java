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
 * 공통코드 그룹 다국어. (TB_CODE_GROUP_LANG)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_CODE_GROUP_LANG")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeGroupLang extends AssignedIdEntity<CodeGroupLangId> {

  @EmbeddedId
  private CodeGroupLangId id; // 그룹코드 + 언어코드

  @Column(name = "GROUP_NM", length = 200, nullable = false)
  private String groupNm; // 그룹명

  /**
   * 그룹 다국어를 생성한다.
   *
   * @param groupCd 그룹코드
   * @param langCd 언어코드
   * @param groupNm 그룹명
   */
  public CodeGroupLang(String groupCd, String langCd, String groupNm) {
    this.id = new CodeGroupLangId(groupCd, langCd);
    this.groupNm = groupNm;
  }

  /**
   * 그룹명을 변경한다.
   *
   * @param groupNm 그룹명
   */
  public void changeName(String groupNm) {
    this.groupNm = groupNm;
  }
}
