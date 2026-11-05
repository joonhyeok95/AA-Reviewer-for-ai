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
 * 부서. (TB_DEPT)
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
  private String deptNm; // 부서명

  @Column(name = "PARENT_DEPT_CD", length = 50)
  private String parentDeptCd; // 상위부서코드

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private Dept(
      String deptCd, String deptNm, String parentDeptCd, Integer sortOrder, String useYn) {
    this.deptCd = deptCd;
    this.deptNm = deptNm;
    this.parentDeptCd = parentDeptCd;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }

  @Override
  public String getId() {
    return deptCd;
  }

  /**
   * 부서 정보를 수정한다.
   *
   * @param deptNm 부서명
   * @param parentDeptCd 상위부서코드
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public void update(String deptNm, String parentDeptCd, Integer sortOrder, String useYn) {
    this.deptNm = deptNm;
    this.parentDeptCd = parentDeptCd;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }
}
