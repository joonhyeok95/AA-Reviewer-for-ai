package com.flowlog.backend.bulletin.domain.repository;

import com.flowlog.backend.bulletin.domain.model.BulletinOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주보 예배순서 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface BulletinOrderRepository extends JpaRepository<BulletinOrder, Long> {

  /**
   * 주보의 예배순서.
   *
   * @param bulletinId 주보 ID
   * @return 예배순서 (순서대로)
   */
  List<BulletinOrder> findByBulletinIdOrderBySortOrderAsc(Long bulletinId);

  /**
   * 주보의 예배순서 전체 삭제. (저장 시 교체)
   *
   * @param bulletinId 주보 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from BulletinOrder o where o.bulletinId = :bulletinId")
  void deleteByBulletinId(@Param("bulletinId") Long bulletinId);
}
