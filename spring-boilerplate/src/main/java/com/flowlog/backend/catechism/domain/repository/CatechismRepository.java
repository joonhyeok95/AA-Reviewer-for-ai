package com.flowlog.backend.catechism.domain.repository;

import com.flowlog.backend.catechism.domain.model.Catechism;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 교리문답 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface CatechismRepository extends JpaRepository<Catechism, Long> {

  /**
   * 전체 교리문답 (정렬순서, ID 순).
   *
   * @return 교리문답
   */
  List<Catechism> findAllByOrderBySortOrderAscIdAsc();

  /**
   * 코드 존재 여부.
   *
   * @param catechismCd 코드
   * @return 존재 여부
   */
  boolean existsByCatechismCd(String catechismCd);
}
