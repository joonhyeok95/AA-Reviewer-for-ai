package com.flowlog.backend.code.domain.repository;

import com.flowlog.backend.code.domain.model.CodeGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공통코드 그룹 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface CodeGroupRepository extends JpaRepository<CodeGroup, String> {

  /**
   * 사용여부로 그룹 목록을 정렬순서대로 조회한다.
   *
   * @param useYn 사용여부
   * @return 그룹 목록
   */
  List<CodeGroup> findByUseYnOrderBySortOrderAsc(String useYn);

  /**
   * 전체 그룹 (미사용 포함, 관리 화면).
   *
   * @return 그룹 목록
   */
  List<CodeGroup> findAllByOrderBySortOrderAscGroupCdAsc();
}
