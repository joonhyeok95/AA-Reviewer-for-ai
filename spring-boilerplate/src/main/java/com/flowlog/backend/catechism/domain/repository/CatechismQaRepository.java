package com.flowlog.backend.catechism.domain.repository;

import com.flowlog.backend.catechism.domain.model.CatechismQa;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 교리문답 문답 Repository. (답 단락·근거 성구 삭제 포함 - 단위 저장 시 하위 전체 교체)
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface CatechismQaRepository extends JpaRepository<CatechismQa, Long> {

  /**
   * 단위들의 문답 (단위, 정렬순서 순).
   *
   * @param unitIds 단위 ID
   * @return 문답
   */
  List<CatechismQa> findByUnitIdInOrderByUnitIdAscSortOrderAscQuestionNoAsc(
      Collection<Long> unitIds);

  /**
   * 단위별 문답 통계 [단위 ID, 건수, 첫 문 번호, 마지막 문 번호].
   *
   * @param catechismId 교리문답 ID
   * @return 통계
   */
  @Query("""
      select q.unitId, count(q), min(q.questionNo), max(q.questionNo) from CatechismQa q
       where q.catechismId = :catechismId
       group by q.unitId
      """)
  List<Object[]> statsByUnit(@Param("catechismId") Long catechismId);

  /**
   * 다른 단위에 같은 문 번호가 있는 것.
   *
   * @param catechismId 교리문답 ID
   * @param questionNos 문 번호
   * @param unitId 제외할 단위 ID (새 단위면 -1)
   * @return 중복 문답
   */
  @Query("""
      select q from CatechismQa q
       where q.catechismId = :catechismId and q.questionNo in :questionNos and q.unitId <> :unitId
      """)
  List<CatechismQa> findDuplicates(@Param("catechismId") Long catechismId,
      @Param("questionNos") Collection<Integer> questionNos, @Param("unitId") Long unitId);

  /**
   * 단위의 근거 성구 삭제.
   *
   * @param unitId 단위 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      delete from CatechismProof p where p.answerId in (
        select a.id from CatechismAnswer a where a.qaId in (
          select q.id from CatechismQa q where q.unitId = :unitId))
      """)
  void deleteProofsOfUnit(@Param("unitId") Long unitId);

  /**
   * 단위의 답 단락 삭제.
   *
   * @param unitId 단위 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      delete from CatechismAnswer a where a.qaId in (
        select q.id from CatechismQa q where q.unitId = :unitId)
      """)
  void deleteAnswersOfUnit(@Param("unitId") Long unitId);

  /**
   * 단위의 문답 삭제. (근거 성구 → 답 단락 → 문답 순서로 호출)
   *
   * @param unitId 단위 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from CatechismQa q where q.unitId = :unitId")
  void deleteQasOfUnit(@Param("unitId") Long unitId);
}
