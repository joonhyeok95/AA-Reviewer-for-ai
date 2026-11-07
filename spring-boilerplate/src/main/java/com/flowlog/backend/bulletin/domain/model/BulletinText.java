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
 * 주보 전례문 Entity. (TB_BULLETIN_TEXT - 주보에 싣는 사도신경·주기도문 등)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_BULLETIN_TEXT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletinText extends AssignedIdEntity<BulletinTextId> {

  @EmbeddedId
  private BulletinTextId id; // 주보 ID + 전례문 ID

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 순서

  /**
   * 생성자.
   *
   * @param bulletinId 주보 ID
   * @param textId 전례문 ID
   * @param sortOrder 순서
   */
  public BulletinText(Long bulletinId, Long textId, Integer sortOrder) {
    this.id = new BulletinTextId(bulletinId, textId);
    this.sortOrder = sortOrder;
  }
}
