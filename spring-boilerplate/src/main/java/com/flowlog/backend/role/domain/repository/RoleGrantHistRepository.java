package com.flowlog.backend.role.domain.repository;

import com.flowlog.backend.role.domain.model.RoleGrantHist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 권한 부여 변경 이력 Repository.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public interface RoleGrantHistRepository extends JpaRepository<RoleGrantHist, Long> {

  /**
   * 권한의 부여 변경 이력. (최신순)
   *
   * @param roleId 권한ID
   * @return 이력
   */
  List<RoleGrantHist> findByRoleIdOrderByIdDesc(Long roleId);
}
