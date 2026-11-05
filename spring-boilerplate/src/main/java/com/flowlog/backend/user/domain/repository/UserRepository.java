package com.flowlog.backend.user.domain.repository;

import com.flowlog.backend.user.domain.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface UserRepository extends JpaRepository<User, String> {

  /**
   * IdP 사용자 고유ID(토큰 sub)로 사용 중인 사용자를 조회한다.
   *
   * @param idpSubject Keycloak user id
   * @param useYn 사용여부
   * @return 사용자
   */
  Optional<User> findByIdpSubjectAndUseYn(String idpSubject, String useYn);

  /**
   * 부서 소속 사용자 (메일 수신자 추출 - 겸직 포함, 사용 중인 사용자만).
   *
   * @param deptCds 부서코드 목록
   * @return 사용자 목록 (중복 제거)
   */
  @Query("""
      select distinct u from User u, UserDept ud
       where ud.id.userId = u.userId
         and ud.id.deptCd in :deptCds
         and u.useYn = 'Y'
      """)
  List<User> findActiveUsersInDepts(@Param("deptCds") Collection<String> deptCds);
}
