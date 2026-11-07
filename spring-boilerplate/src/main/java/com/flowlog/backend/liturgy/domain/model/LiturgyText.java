package com.flowlog.backend.liturgy.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전례문 Entity. (TB_LITURGY_TEXT - 사도신경, 주기도문, 교독문 등 매주 재사용하는 본문)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_LITURGY_TEXT")
@SequenceGenerator(name = "SQ_LITURGY_TEXT_GEN", sequenceName = "SQ_LITURGY_TEXT", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiturgyText extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_LITURGY_TEXT_GEN")
  @Column(name = "TEXT_ID")
  private Long id; // 전례문 ID

  @Column(name = "TEXT_TYPE_CD", length = 30, nullable = false)
  private String textTypeCd; // 유형 (공통코드 LITURGY_TYPE)

  @Column(name = "TITLE", length = 200, nullable = false)
  private String title; // 제목

  @Column(name = "VERSION_NM", length = 100)
  private String versionNm; // 번역·판

  @Column(name = "INTRO", length = 1000)
  private String intro; // 머리말

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private LiturgyText(String textTypeCd, String title, String versionNm, String intro,
      Integer sortOrder, String useYn) {
    update(textTypeCd, title, versionNm, intro, sortOrder, useYn);
  }

  /**
   * 전례문 정보를 수정한다.
   *
   * @param textTypeCd 유형
   * @param title 제목
   * @param versionNm 번역·판
   * @param intro 머리말
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public void update(String textTypeCd, String title, String versionNm, String intro,
      Integer sortOrder, String useYn) {
    this.textTypeCd = textTypeCd;
    this.title = title;
    this.versionNm = versionNm;
    this.intro = intro;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }
}
