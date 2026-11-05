package com.flowlog.backend.user.domain.repository;

import com.flowlog.backend.user.domain.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
