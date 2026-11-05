package com.flowlog.backend.role.domain.repository;

import com.flowlog.backend.role.domain.model.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 권한 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

  /**
   * 권한코드로 조회한다.
   *
   * @param roleCd 권한코드
   * @return 권한
   */
  Optional<Role> findByRoleCd(String roleCd);

  /**
   * 권한코드 존재 여부.
   *
   * @param roleCd 권한코드
   * @return 존재 여부
   */
  boolean existsByRoleCd(String roleCd);

  /**
   * 모듈별 권한 목록을 조회한다.
   *
   * @param moduleCd 모듈코드
   * @return 권한 목록 (레벨, 정렬순서 순)
   */
  List<Role> findByModuleCdOrderByRoleLevelAscSortOrderAsc(String moduleCd);

  /**
   * 사용자의 유효 권한을 조회한다. (직접 부여 + 소속부서 부여, 기준일에 유효한 것만)
   *
   * @param userId 사용자ID
   * @param baseDate 기준일 (보통 오늘)
   * @return 유효 권한 목록 (상위 레벨 순)
   */
  @Query("""
      select r from Role r
       where r.useYn = 'Y'
         and (r.id in (select ur.roleId from UserRole ur
                        where ur.userId = :userId
                          and :baseDate between ur.startDt and ur.endDt)
           or r.id in (select dr.roleId from DeptRole dr, UserDept ud
                        where ud.id.deptCd = dr.deptCd
                          and ud.id.userId = :userId
                          and :baseDate between dr.startDt and dr.endDt))
       order by r.roleLevel, r.sortOrder
      """)
  List<Role> findEffectiveRoles(
      @Param("userId") String userId, @Param("baseDate") LocalDate baseDate);
}
