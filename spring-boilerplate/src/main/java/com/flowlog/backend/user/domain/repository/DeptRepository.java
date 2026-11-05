package com.flowlog.backend.user.domain.repository;

import com.flowlog.backend.user.domain.model.Dept;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 부서 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface DeptRepository extends JpaRepository<Dept, String> {

  /**
   * 하위 부서 목록을 조회한다.
   *
   * @param parentDeptCd 상위부서코드
   * @return 하위 부서 목록
   */
  List<Dept> findByParentDeptCdOrderBySortOrderAsc(String parentDeptCd);
}
