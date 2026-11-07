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
 * 교리문답 근거 성구 Entity. (TB_CATECHISM_PROOF - 답 단락별)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_CATECHISM_PROOF")
@SequenceGenerator(
    name = "SQ_CATECHISM_PROOF_GEN", sequenceName = "SQ_CATECHISM_PROOF", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatechismProof extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_CATECHISM_PROOF_GEN")
  @Column(name = "PROOF_ID")
  private Long id; // ID

  @Column(name = "ANSWER_ID", nullable = false)
  private Long answerId; // 답 단락 ID

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 순서

  @Column(name = "BIBLE_REF", length = 100, nullable = false)
  private String bibleRef; // 성경 구절

  @Column(name = "BIBLE_TEXT", length = 2000)
  private String bibleText; // 구절 본문

  /**
   * 생성자.
   *
   * @param answerId 답 단락 ID
   * @param sortOrder 순서
   * @param bibleRef 성경 구절
   * @param bibleText 구절 본문
   */
  public CatechismProof(Long answerId, Integer sortOrder, String bibleRef, String bibleText) {
    this.answerId = answerId;
    this.sortOrder = sortOrder;
    this.bibleRef = bibleRef;
    this.bibleText = bibleText;
  }
}
