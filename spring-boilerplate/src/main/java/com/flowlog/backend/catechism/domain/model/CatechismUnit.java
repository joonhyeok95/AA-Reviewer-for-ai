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
 * 교리문답 단위 Entity. (TB_CATECHISM_UNIT - 부 → 주일 / 장 트리)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_CATECHISM_UNIT")
@SequenceGenerator(
    name = "SQ_CATECHISM_UNIT_GEN", sequenceName = "SQ_CATECHISM_UNIT", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatechismUnit extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_CATECHISM_UNIT_GEN")
  @Column(name = "UNIT_ID")
  private Long id; // ID

  @Column(name = "CATECHISM_ID", nullable = false)
  private Long catechismId; // 교리문답 ID

  @Column(name = "PARENT_UNIT_ID")
  private Long parentUnitId; // 상위 단위 ID (최상위 null)

  @Column(name = "UNIT_TYPE_CD", length = 30, nullable = false)
  private String unitTypeCd; // 단위 유형 (공통코드 CATECHISM_UNIT_TYPE)

  @Column(name = "UNIT_NO")
  private Integer unitNo; // 번호 (제45주일 → 45)

  @Column(name = "TITLE", length = 200)
  private String title; // 제목

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 같은 상위 안의 정렬순서

  /**
   * 생성자.
   *
   * @param catechismId 교리문답 ID
   * @param parentUnitId 상위 단위 ID (최상위 null)
   * @param unitTypeCd 단위 유형 (공통코드 CATECHISM_UNIT_TYPE)
   * @param unitNo 번호 (제45주일 → 45)
   * @param title 제목
   * @param sortOrder 같은 상위 안의 정렬순서
   */
  public CatechismUnit(
      Long catechismId, Long parentUnitId, String unitTypeCd, Integer unitNo, String title,
      Integer sortOrder) {
    this.catechismId = catechismId;
    this.parentUnitId = parentUnitId;
    this.unitTypeCd = unitTypeCd;
    this.unitNo = unitNo;
    this.title = title;
    this.sortOrder = sortOrder;
  }

  /**
   * 정보를 수정한다.
   *
   * @param parentUnitId 상위 단위 ID (최상위 null)
   * @param unitTypeCd 단위 유형 (공통코드 CATECHISM_UNIT_TYPE)
   * @param unitNo 번호 (제45주일 → 45)
   * @param title 제목
   * @param sortOrder 같은 상위 안의 정렬순서
   */
  public void update(
      Long parentUnitId, String unitTypeCd, Integer unitNo, String title, Integer sortOrder) {
    this.parentUnitId = parentUnitId;
    this.unitTypeCd = unitTypeCd;
    this.unitNo = unitNo;
    this.title = title;
    this.sortOrder = sortOrder;
  }
}
