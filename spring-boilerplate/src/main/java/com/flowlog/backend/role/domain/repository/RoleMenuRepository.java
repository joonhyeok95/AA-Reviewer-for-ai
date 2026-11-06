package com.flowlog.backend.role.domain.repository;

import com.flowlog.backend.role.domain.model.RoleMenu;
import com.flowlog.backend.role.domain.model.RoleMenuId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 권한별 메뉴 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface RoleMenuRepository extends JpaRepository<RoleMenu, RoleMenuId> {

  /**
   * 여러 권한의 메뉴 권한을 조회한다. (사용자 유효 권한의 메뉴 권한 합산용)
   *
   * @param roleIds 권한ID 목록
   * @return 메뉴 권한 목록
   */
  List<RoleMenu> findByIdRoleIdIn(Collection<Long> roleIds);

  /**
   * 메뉴에 매핑된 권한 목록을 조회한다.
   *
   * @param menuId 메뉴ID
   * @return 메뉴 권한 목록
   */
  List<RoleMenu> findByIdMenuId(Long menuId);

  /**
   * 권한의 메뉴 접근.
   *
   * @param roleId 권한ID
   * @return 메뉴 접근 목록
   */
  List<RoleMenu> findByIdRoleId(Long roleId);

  /**
   * 권한별 접근 메뉴 수.
   *
   * @return [권한ID, 건수]
   */
  @Query("select rm.id.roleId, count(rm) from RoleMenu rm group by rm.id.roleId")
  List<Object[]> countByRole();
}
