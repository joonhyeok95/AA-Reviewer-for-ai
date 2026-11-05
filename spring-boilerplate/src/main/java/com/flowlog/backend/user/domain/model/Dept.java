package com.flowlog.backend.user.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서 (인사 마스터 조직도). (TB_DEPT)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_DEPT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dept extends AssignedIdEntity<String> {

  @Id
  @Column(name = "DEPT_CD", length = 50)
  private String deptCd; // 부서코드

  @Column(name = "DEPT_NM", length = 200, nullable = false)
  private String deptNm; // 부서명 (국문)

  @Column(name = "DEPT_NM_EN", length = 200)
  private String deptNmEn; // 부서명 (영문)

  @Column(name = "WORKPLACE_CD", length = 50)
  private String workplaceCd; // 사업장코드 (TB_WORKPLACE)

  @Column(name = "PARENT_DEPT_CD", length = 50)
  private String parentDeptCd; // 상위부서코드

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private Dept(
      String deptCd, String deptNm, String deptNmEn, String workplaceCd, String parentDeptCd,
      Integer sortOrder, String useYn) {
    this.deptCd = deptCd;
    this.deptNm = deptNm;
    this.deptNmEn = deptNmEn;
    this.workplaceCd = workplaceCd;
    this.parentDeptCd = parentDeptCd;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }

  @Override
  public String getId() {
    return deptCd;
  }

  /**
   * 부서 정보를 수정한다. (인사 마스터 동기화)
   *
   * @param deptNm 부서명 (국문)
   * @param deptNmEn 부서명 (영문)
   * @param workplaceCd 사업장코드
   * @param parentDeptCd 상위부서코드
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public void update(
      String deptNm, String deptNmEn, String workplaceCd, String parentDeptCd, Integer sortOrder,
      String useYn) {
    this.deptNm = deptNm;
    this.deptNmEn = deptNmEn;
    this.workplaceCd = workplaceCd;
    this.parentDeptCd = parentDeptCd;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }

  /**
   * 언어별 부서명. (ko = 국문, 그 외 = 영문명, 영문명이 없으면 국문)
   *
   * @param langCd 언어코드
   * @return 부서명
   */
  public String nameOf(String langCd) {
    return Workplace.localizedName(deptNm, deptNmEn, langCd);
  }
}
