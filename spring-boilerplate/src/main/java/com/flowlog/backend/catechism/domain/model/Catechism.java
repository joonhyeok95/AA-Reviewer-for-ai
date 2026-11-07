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
 * 교리문답 Entity. (TB_CATECHISM - 하이델베르크, 웨스트민스터 소요리 등)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_CATECHISM")
@SequenceGenerator(name = "SQ_CATECHISM_GEN", sequenceName = "SQ_CATECHISM", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Catechism extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_CATECHISM_GEN")
  @Column(name = "CATECHISM_ID")
  private Long id; // ID

  @Column(name = "CATECHISM_CD", length = 30, nullable = false)
  private String catechismCd; // 교리문답 코드

  @Column(name = "TITLE", length = 200, nullable = false)
  private String title; // 이름

  @Column(name = "DESCRIPTION", length = 1000)
  private String description; // 설명

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  /**
   * 생성자.
   *
   * @param catechismCd 교리문답 코드
   * @param title 이름
   * @param description 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public Catechism(
      String catechismCd, String title, String description, Integer sortOrder, String useYn) {
    this.catechismCd = catechismCd;
    this.title = title;
    this.description = description;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }

  /**
   * 정보를 수정한다.
   *
   * @param title 이름
   * @param description 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public void update(String title, String description, Integer sortOrder, String useYn) {
    this.title = title;
    this.description = description;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }
}
