package com.flowlog.backend.role.domain.repository;

import com.flowlog.backend.role.domain.model.RoleLang;
import com.flowlog.backend.role.domain.model.RoleLangId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 권한 다국어 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface RoleLangRepository extends JpaRepository<RoleLang, RoleLangId> {

  /**
   * 권한들의 특정 언어 이름을 조회한다.
   *
   * @param roleIds 권한ID 목록
   * @param langCd 언어코드
   * @return 다국어 목록
   */
  List<RoleLang> findByIdRoleIdInAndIdLangCd(Collection<Long> roleIds, String langCd);
}
