package com.flowlog.backend.role.domain.repository;

import com.flowlog.backend.role.domain.model.DeptRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 부서별 권한 부여 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface DeptRoleRepository extends JpaRepository<DeptRole, Long> {

  /**
   * 부서의 권한 부여 이력을 조회한다. (만료/예정 포함)
   *
   * @param deptCd 부서코드
   * @return 부여 이력 (최근 시작일 순)
   */
  List<DeptRole> findByDeptCdOrderByStartDtDesc(String deptCd);

  /**
   * 권한이 부여된 부서 목록을 조회한다.
   *
   * @param roleId 권한ID
   * @return 부여 목록
   */
  List<DeptRole> findByRoleId(Long roleId);
}
