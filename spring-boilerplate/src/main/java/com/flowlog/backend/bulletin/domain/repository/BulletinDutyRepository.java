package com.flowlog.backend.bulletin.domain.repository;

import com.flowlog.backend.bulletin.domain.model.BulletinDuty;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주보 섬김위원 Repository.
 *
 * @author flowlog
 * @since 2026-11-08
 */
public interface BulletinDutyRepository extends JpaRepository<BulletinDuty, Long> {

  /**
   * 주보의 섬김위원.
   *
   * @param bulletinId 주보 ID
   * @return 섬김위원 (역할 순서, 날짜 순)
   */
  List<BulletinDuty> findByBulletinIdOrderByRoleOrderAscDutyDtAsc(Long bulletinId);

  /**
   * 주보의 섬김위원 전체 삭제. (저장 시 교체)
   *
   * @param bulletinId 주보 ID
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from BulletinDuty d where d.bulletinId = :bulletinId")
  void deleteByBulletinId(@Param("bulletinId") Long bulletinId);
}
