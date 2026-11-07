package com.flowlog.backend.catechism.domain.model;

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
 * 교리문답 문답 Entity. (TB_CATECHISM_QA - 문 번호·질문)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_CATECHISM_QA")
@SequenceGenerator(
    name = "SQ_CATECHISM_QA_GEN", sequenceName = "SQ_CATECHISM_QA", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatechismQa extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_CATECHISM_QA_GEN")
  @Column(name = "QA_ID")
  private Long id; // ID

  @Column(name = "CATECHISM_ID", nullable = false)
  private Long catechismId; // 교리문답 ID

  @Column(name = "UNIT_ID", nullable = false)
  private Long unitId; // 소속 단위 ID

  @Column(name = "QUESTION_NO", nullable = false)
  private Integer questionNo; // 문 번호 (교리문답 안에서 유일)

  @Column(name = "QUESTION", length = 2000, nullable = false)
  private String question; // 질문

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 단위 안의 정렬순서

  /**
   * 생성자.
   *
   * @param catechismId 교리문답 ID
   * @param unitId 소속 단위 ID
   * @param questionNo 문 번호 (교리문답 안에서 유일)
   * @param question 질문
   * @param sortOrder 단위 안의 정렬순서
   */
  public CatechismQa(
      Long catechismId, Long unitId, Integer questionNo, String question, Integer sortOrder) {
    this.catechismId = catechismId;
    this.unitId = unitId;
    this.questionNo = questionNo;
    this.question = question;
    this.sortOrder = sortOrder;
  }
}
