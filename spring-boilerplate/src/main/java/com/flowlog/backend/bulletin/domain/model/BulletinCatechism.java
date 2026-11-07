package com.flowlog.backend.bulletin.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보 교리문답 Entity. (TB_BULLETIN_CATECHISM - 주보에 싣는 교리문답 단위)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_BULLETIN_CATECHISM")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletinCatechism extends AssignedIdEntity<BulletinCatechismId> {

  @EmbeddedId
  private BulletinCatechismId id; // 주보 ID + 교리문답 단위 ID

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 순서

  /**
   * 생성자.
   *
   * @param bulletinId 주보 ID
   * @param unitId 교리문답 단위 ID
   * @param sortOrder 순서
   */
  public BulletinCatechism(Long bulletinId, Long unitId, Integer sortOrder) {
    this.id = new BulletinCatechismId(bulletinId, unitId);
    this.sortOrder = sortOrder;
  }
}
