package com.flowlog.backend.catechism.domain.repository;

import com.flowlog.backend.catechism.domain.model.CatechismAnswer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 교리문답 답 단락 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface CatechismAnswerRepository extends JpaRepository<CatechismAnswer, Long> {

  /**
   * 문답들의 답 단락 (문답, 순서 순).
   *
   * @param qaIds 문답 ID
   * @return 답 단락
   */
  List<CatechismAnswer> findByQaIdInOrderByQaIdAscSortOrderAsc(Collection<Long> qaIds);
}
