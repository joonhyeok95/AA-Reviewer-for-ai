package com.flowlog.backend.role.domain.model;

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
 * 사용자별 권한 부여. (TB_USER_ROLE)
 *
 * <p>같은 권한을 기간을 달리해 재부여한 이력을 남기기 위해 대리키(USER_ROLE_ID)를 사용한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_USER_ROLE")
@SequenceGenerator(name = "SQ_USER_ROLE_GEN", sequenceName = "SQ_USER_ROLE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole extends BaseRoleGrant {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_USER_ROLE_GEN")
  @Column(name = "USER_ROLE_ID")
  private Long id; // 사용자권한ID

  @Column(name = "USER_ID", length = 50, nullable = false, updatable = false)
  private String userId; // 사용자ID

  @Builder
  private UserRole(
      String userId, Long roleId, LocalDate startDt, LocalDate endDt, String grantReason) {
    super(roleId, startDt, endDt, grantReason);
    this.userId = userId;
  }
}
