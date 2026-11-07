package com.flowlog.backend.bulletin.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보 예배순서 Entity. (TB_BULLETIN_ORDER)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_BULLETIN_ORDER")
@SequenceGenerator(
    name = "SQ_BULLETIN_ORDER_GEN", sequenceName = "SQ_BULLETIN_ORDER", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletinOrder extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_BULLETIN_ORDER_GEN")
  @Column(name = "ORDER_ID")
  private Long id; // ID

  @Column(name = "BULLETIN_ID", nullable = false)
  private Long bulletinId; // 주보 ID

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 순서

  @Column(name = "ITEM_NM", length = 100, nullable = false)
  private String itemNm; // 항목

  @Column(name = "DETAIL", length = 500)
  private String detail; // 담당·내용

  /**
   * 생성자.
   *
   * @param bulletinId 주보 ID
   * @param sortOrder 순서
   * @param itemNm 항목
   * @param detail 담당·내용
   */
  public BulletinOrder(
      Long bulletinId, Integer sortOrder, String itemNm, String detail) {
    this.bulletinId = bulletinId;
    this.sortOrder = sortOrder;
    this.itemNm = itemNm;
    this.detail = detail;
  }
}
