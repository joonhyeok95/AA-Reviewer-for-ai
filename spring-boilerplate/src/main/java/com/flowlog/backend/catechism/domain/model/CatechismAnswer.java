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
 * 교리문답 답 단락 Entity. (TB_CATECHISM_ANSWER - 첫째, 둘째 …)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_CATECHISM_ANSWER")
@SequenceGenerator(
    name = "SQ_CATECHISM_ANSWER_GEN", sequenceName = "SQ_CATECHISM_ANSWER", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatechismAnswer extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_CATECHISM_ANSWER_GEN")
  @Column(name = "ANSWER_ID")
  private Long id; // ID

  @Column(name = "QA_ID", nullable = false)
  private Long qaId; // 문답 ID

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 단락 순서

  @Column(name = "CONTENT", length = 4000, nullable = false)
  private String content; // 답 내용

  /**
   * 생성자.
   *
   * @param qaId 문답 ID
   * @param sortOrder 단락 순서
   * @param content 답 내용
   */
  public CatechismAnswer(Long qaId, Integer sortOrder, String content) {
    this.qaId = qaId;
    this.sortOrder = sortOrder;
    this.content = content;
  }
}
