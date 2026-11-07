package com.flowlog.backend.catechism.domain.repository;

import com.flowlog.backend.catechism.domain.model.CatechismUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 교리문답 단위 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface CatechismUnitRepository extends JpaRepository<CatechismUnit, Long> {

  /**
   * 교리문답의 전체 단위 (정렬순서, ID 순 - 트리는 서비스에서 조립).
   *
   * @param catechismId 교리문답 ID
   * @return 단위
   */
  List<CatechismUnit> findByCatechismIdOrderBySortOrderAscIdAsc(Long catechismId);

  /**
   * 전체 단위 (정렬순서, ID 순).
   *
   * @return 단위
   */
  List<CatechismUnit> findAllByOrderBySortOrderAscIdAsc();

  /**
   * 하위 단위 존재 여부.
   *
   * @param parentUnitId 상위 단위 ID
   * @return 존재 여부
   */
  boolean existsByParentUnitId(Long parentUnitId);

  /**
   * 교리문답에 단위가 있는지.
   *
   * @param catechismId 교리문답 ID
   * @return 존재 여부
   */
  boolean existsByCatechismId(Long catechismId);
}
