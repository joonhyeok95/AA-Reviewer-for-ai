package com.flowlog.backend.bulletin.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보 섬김위원 Entity. (TB_BULLETIN_DUTY - 역할 × 날짜 = 담당자)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_BULLETIN_DUTY")
@SequenceGenerator(
    name = "SQ_BULLETIN_DUTY_GEN", sequenceName = "SQ_BULLETIN_DUTY", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletinDuty extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_BULLETIN_DUTY_GEN")
  @Column(name = "DUTY_ID")
  private Long id; // ID

  @Column(name = "BULLETIN_ID", nullable = false)
  private Long bulletinId; // 주보 ID

  @Column(name = "ROLE_NM", length = 50, nullable = false)
  private String roleNm; // 역할

  @Column(name = "ROLE_ORDER", nullable = false)
  private Integer roleOrder; // 역할 순서

  @Column(name = "DUTY_DT", nullable = false)
  private LocalDate dutyDt; // 날짜

  @Column(name = "MEMBERS", length = 200)
  private String members; // 담당자

  /**
   * 생성자.
   *
   * @param bulletinId 주보 ID
   * @param roleNm 역할
   * @param roleOrder 역할 순서
   * @param dutyDt 날짜
   * @param members 담당자
   */
  public BulletinDuty(
      Long bulletinId, String roleNm, Integer roleOrder, LocalDate dutyDt, String members) {
    this.bulletinId = bulletinId;
    this.roleNm = roleNm;
    this.roleOrder = roleOrder;
    this.dutyDt = dutyDt;
    this.members = members;
  }
}
