package com.flowlog.backend.bulletin.domain.repository;

import com.flowlog.backend.bulletin.domain.model.BulletinText;
import com.flowlog.backend.bulletin.domain.model.BulletinTextId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주보 전례문 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface BulletinTextRepository extends JpaRepository<BulletinText, BulletinTextId> {

  /**
   * 주보의 전례문.
   *
   * @param bulletinId 주보 ID
   * @return 연결 (순서대로)
   */
  List<BulletinText> findByIdBulletinIdOrderBySortOrderAsc(Long bulletinId);

  /**
   * 전례문을 쓰는 주보 수. (전례문 삭제 전 확인)
   *
   * @param textId 전례문 ID
   * @return 주보 수
   */
  long countByIdTextId(Long textId);

  /**
   * 주보의 전례문 연결 전체 삭제. (저장 시 교체)
   *
   * @param bulletinId 주보 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from BulletinText t where t.id.bulletinId = :bulletinId")
  void deleteByBulletinId(@Param("bulletinId") Long bulletinId);
}
