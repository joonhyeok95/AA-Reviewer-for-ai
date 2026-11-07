package com.flowlog.backend.liturgy.domain.repository;

import com.flowlog.backend.liturgy.domain.model.LiturgyLine;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 전례문 줄 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface LiturgyLineRepository extends JpaRepository<LiturgyLine, Long> {

  /**
   * 전례문들의 줄 (전례문, 순서 순).
   *
   * @param textIds 전례문 ID
   * @return 줄
   */
  List<LiturgyLine> findByTextIdInOrderByTextIdAscSortOrderAsc(Collection<Long> textIds);

  /**
   * 전례문의 줄 전체 삭제. (저장 시 교체 - 즉시 실행)
   *
   * @param textId 전례문 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from LiturgyLine l where l.textId = :textId")
  void deleteByTextId(@Param("textId") Long textId);
}
