package com.flowlog.backend.role.domain.repository;

import com.flowlog.backend.role.domain.model.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 사용자별 권한 부여 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

  /**
   * 사용자의 권한 부여 이력을 조회한다. (만료/예정 포함)
   *
   * @param userId 사용자ID
   * @return 부여 이력 (최근 시작일 순)
   */
  List<UserRole> findByUserIdOrderByStartDtDesc(String userId);

  /**
   * 권한이 부여된 사용자 목록을 조회한다.
   *
   * @param roleId 권한ID
   * @return 부여 목록
   */
  List<UserRole> findByRoleId(Long roleId);

  /**
   * 권한별 사용자 부여 건수.
   *
   * @return [권한ID, 건수]
   */
  @Query("select ur.roleId, count(ur) from UserRole ur group by ur.roleId")
  List<Object[]> countByRole();
}
