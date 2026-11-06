package com.flowlog.backend.user.domain.repository;

import com.flowlog.backend.user.domain.model.Workplace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사업장 Repository. (인사 마스터)
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface WorkplaceRepository extends JpaRepository<Workplace, String> {

  /**
   * 사용 중인 사업장 목록.
   *
   * @param useYn 사용여부
   * @return 사업장 목록 (정렬순서)
   */
  List<Workplace> findByUseYnOrderBySortOrderAsc(String useYn);
}
