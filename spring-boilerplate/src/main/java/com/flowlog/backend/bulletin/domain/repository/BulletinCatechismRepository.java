package com.flowlog.backend.bulletin.domain.repository;

import com.flowlog.backend.bulletin.domain.model.BulletinCatechism;
import com.flowlog.backend.bulletin.domain.model.BulletinCatechismId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주보 교리문답 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface BulletinCatechismRepository
    extends JpaRepository<BulletinCatechism, BulletinCatechismId> {

  /**
   * 주보의 교리문답 단위.
   *
   * @param bulletinId 주보 ID
   * @return 연결 (순서대로)
   */
  List<BulletinCatechism> findByIdBulletinIdOrderBySortOrderAsc(Long bulletinId);

  /**
   * 단위를 쓰는 주보 수. (단위 삭제 전 확인)
   *
   * @param unitId 단위 ID
   * @return 주보 수
   */
  long countByIdUnitId(Long unitId);

  /**
   * 주보의 교리문답 연결 전체 삭제. (저장 시 교체 - 즉시 실행이라 같은 PK 재등록 가능)
   *
   * @param bulletinId 주보 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from BulletinCatechism c where c.id.bulletinId = :bulletinId")
  void deleteByBulletinId(@Param("bulletinId") Long bulletinId);
}
