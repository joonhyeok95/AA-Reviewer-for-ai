package com.flowlog.backend.role.domain.model;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 부여 변경 이력. (TB_ROLE_GRANT_HIST - 감사용, 추가만 하고 수정·삭제하지 않음)
 *
 * <p>변경자·변경일시는 감사 컬럼(CREATED_BY / CREATED_AT)을 쓴다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Getter
@Entity
@Table(name = "TB_ROLE_GRANT_HIST")
@SequenceGenerator(name = "SQ_ROLE_GRANT_HIST_GEN", sequenceName = "SQ_ROLE_GRANT_HIST",
    allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleGrantHist extends BaseEntity {

  /** 부여. */
  public static final String ACTION_GRANT = "GRANT";

  /** 기간·사유 수정. */
  public static final String ACTION_UPDATE = "UPDATE";

  /** 회수. */
  public static final String ACTION_REVOKE = "REVOKE";

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_ROLE_GRANT_HIST_GEN")
  @Column(name = "HIST_ID")
  private Long id; // 이력ID

  @Column(name = "ROLE_ID", nullable = false, updatable = false)
  private Long roleId; // 권한ID

  @Column(name = "TARGET_TYPE", length = 10, nullable = false, updatable = false)
  private String targetType; // USER / DEPT

  @Column(name = "TARGET_ID", length = 50, nullable = false, updatable = false)
  private String targetId; // 사용자ID / 부서코드

  @Column(name = "GRANT_ID", nullable = false, updatable = false)
  private Long grantId; // 부여ID

  @Column(name = "ACTION_CD", length = 10, nullable = false, updatable = false)
  private String actionCd; // GRANT / UPDATE / REVOKE

  @Column(name = "START_DT", nullable = false, updatable = false)
  private LocalDate startDt; // 시작일

  @Column(name = "END_DT", nullable = false, updatable = false)
  private LocalDate endDt; // 종료일

  @Column(name = "REASON", length = 1000, updatable = false)
  private String reason; // 사유

  @Builder
  private RoleGrantHist(Long roleId, String targetType, String targetId, Long grantId,
      String actionCd, LocalDate startDt, LocalDate endDt, String reason) {
    this.roleId = roleId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.grantId = grantId;
    this.actionCd = actionCd;
    this.startDt = startDt;
    this.endDt = endDt;
    this.reason = reason;
  }
}
