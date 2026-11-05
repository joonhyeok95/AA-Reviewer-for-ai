package com.flowlog.backend.role.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서(그룹)별 권한 부여. (TB_DEPT_ROLE) - 해당 부서 소속 사용자 전원에게 적용된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_DEPT_ROLE")
@SequenceGenerator(name = "SQ_DEPT_ROLE_GEN", sequenceName = "SQ_DEPT_ROLE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeptRole extends BaseRoleGrant {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_DEPT_ROLE_GEN")
  @Column(name = "DEPT_ROLE_ID")
  private Long id; // 부서권한ID

  @Column(name = "DEPT_CD", length = 50, nullable = false, updatable = false)
  private String deptCd; // 부서코드

  @Builder
  private DeptRole(
      String deptCd, Long roleId, LocalDate startDt, LocalDate endDt, String grantReason) {
    super(roleId, startDt, endDt, grantReason);
    this.deptCd = deptCd;
  }
}
