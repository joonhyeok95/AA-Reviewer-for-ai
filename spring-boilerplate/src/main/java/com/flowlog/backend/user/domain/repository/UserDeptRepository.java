package com.flowlog.backend.user.domain.repository;

import com.flowlog.backend.user.domain.model.UserDept;
import com.flowlog.backend.user.domain.model.UserDeptId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 소속부서 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface UserDeptRepository extends JpaRepository<UserDept, UserDeptId> {

  /**
   * 사용자의 소속부서 목록을 조회한다.
   *
   * @param userId 사용자ID
   * @return 소속부서 목록
   */
  List<UserDept> findByIdUserId(String userId);

  /**
   * 부서의 소속 사용자 목록을 조회한다.
   *
   * @param deptCd 부서코드
   * @return 소속 사용자 목록
   */
  List<UserDept> findByIdDeptCd(String deptCd);

  /**
   * 여러 사용자의 소속. (권한 부여 목록의 부서명)
   *
   * @param userIds 사용자ID 목록
   * @return 소속 목록
   */
  List<UserDept> findByIdUserIdIn(Collection<String> userIds);
}
