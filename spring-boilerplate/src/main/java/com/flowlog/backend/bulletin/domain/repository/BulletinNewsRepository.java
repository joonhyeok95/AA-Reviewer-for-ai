package com.flowlog.backend.bulletin.domain.repository;

import com.flowlog.backend.bulletin.domain.model.BulletinNews;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주보 교회소식 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface BulletinNewsRepository extends JpaRepository<BulletinNews, Long> {

  /**
   * 주보의 교회소식 전체 (트리는 서비스에서 조립).
   *
   * @param bulletinId 주보 ID
   * @return 교회소식 (순서, ID 순)
   */
  List<BulletinNews> findByBulletinIdOrderBySortOrderAscIdAsc(Long bulletinId);

  /**
   * 상위 연결 해제. (자기 참조 FK 때문에 삭제 전에 호출)
   *
   * @param bulletinId 주보 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update BulletinNews n set n.parentNewsId = null where n.bulletinId = :bulletinId")
  void detachParents(@Param("bulletinId") Long bulletinId);

  /**
   * 주보의 교회소식 전체 삭제. ({@link #detachParents} 다음에 호출)
   *
   * @param bulletinId 주보 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from BulletinNews n where n.bulletinId = :bulletinId")
  void deleteByBulletinId(@Param("bulletinId") Long bulletinId);
}
