package com.flowlog.backend.catechism.domain.repository;

import com.flowlog.backend.catechism.domain.model.CatechismProof;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 교리문답 근거 성구 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface CatechismProofRepository extends JpaRepository<CatechismProof, Long> {

  /**
   * 답 단락들의 근거 성구 (답 단락, 순서 순).
   *
   * @param answerIds 답 단락 ID
   * @return 근거 성구
   */
  List<CatechismProof> findByAnswerIdInOrderByAnswerIdAscSortOrderAsc(Collection<Long> answerIds);
}
