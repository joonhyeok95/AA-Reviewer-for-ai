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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전례문 줄 Entity. (TB_LITURGY_LINE - 화자별 교독 한 줄/단락)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_LITURGY_LINE")
@SequenceGenerator(name = "SQ_LITURGY_LINE_GEN", sequenceName = "SQ_LITURGY_LINE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiturgyLine extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_LITURGY_LINE_GEN")
  @Column(name = "LINE_ID")
  private Long id; // 줄 ID

  @Column(name = "TEXT_ID", nullable = false)
  private Long textId; // 전례문 ID

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 순서

  @Column(name = "SPEAKER_CD", length = 30)
  private String speakerCd; // 화자 (공통코드 LITURGY_SPEAKER, NULL = 표시 안 함)

  @Column(name = "CONTENT", length = 2000, nullable = false)
  private String content; // 내용

  /**
   * 생성자.
   *
   * @param textId 전례문 ID
   * @param sortOrder 순서
   * @param speakerCd 화자
   * @param content 내용
   */
  public LiturgyLine(Long textId, Integer sortOrder, String speakerCd, String content) {
    this.textId = textId;
    this.sortOrder = sortOrder;
    this.speakerCd = speakerCd;
    this.content = content;
  }
}
