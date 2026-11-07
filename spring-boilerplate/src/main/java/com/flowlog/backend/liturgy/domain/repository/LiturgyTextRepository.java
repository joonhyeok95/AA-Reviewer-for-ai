package com.flowlog.backend.liturgy.domain.repository;

import com.flowlog.backend.liturgy.domain.model.LiturgyText;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 전례문 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface LiturgyTextRepository extends JpaRepository<LiturgyText, Long> {

  /**
   * 전체 전례문 (정렬순서, ID 순).
   *
   * @return 전례문
   */
  List<LiturgyText> findAllByOrderBySortOrderAscIdAsc();
}
