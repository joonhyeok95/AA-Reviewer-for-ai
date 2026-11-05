package com.flowlog.backend.role.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import com.flowlog.backend.global.constant.DateConstants;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 부여 공통 (권한ID + 유효기간). 사용자 부여/부서 부여가 상속한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseRoleGrant extends BaseEntity {

  @Column(name = "ROLE_ID", nullable = false, updatable = false)
  private Long roleId; // 권한ID

  @Column(name = "START_DT", nullable = false)
  private LocalDate startDt; // 권한시작일

  @Column(name = "END_DT", nullable = false)
  private LocalDate endDt; // 권한종료일 (무기한: 9999-12-31)

  @Column(name = "GRANT_REASON", length = 1000)
  private String grantReason; // 부여사유

  /**
   * 권한 부여 정보를 초기화한다.
   *
   * @param roleId 권한ID
   * @param startDt 시작일
   * @param endDt 종료일 (null 이면 무기한)
   * @param grantReason 부여사유
   */
  protected BaseRoleGrant(Long roleId, LocalDate startDt, LocalDate endDt, String grantReason) {
    this.roleId = roleId;
    this.grantReason = grantReason;
    changePeriod(startDt, endDt);
  }

  /**
   * 유효기간을 변경한다.
   *
   * @param startDt 시작일
   * @param endDt 종료일 (null 이면 무기한)
   * @throws IllegalArgumentException 시작일이 없거나 종료일보다 늦은 경우
   */
  public void changePeriod(LocalDate startDt, LocalDate endDt) {
    LocalDate end = endDt == null ? DateConstants.MAX_END_DATE : endDt;
    if (startDt == null || startDt.isAfter(end)) {
      throw new IllegalArgumentException("권한 시작일은 종료일보다 늦을 수 없습니다.");
    }
    this.startDt = startDt;
    this.endDt = end;
  }

  /**
   * 기준일에 유효한 권한인지 확인한다.
   *
   * @param baseDate 기준일
   * @return 시작일 &lt;= 기준일 &lt;= 종료일 이면 true
   */
  public boolean isValidOn(LocalDate baseDate) {
    return !baseDate.isBefore(startDt) && !baseDate.isAfter(endDt);
  }
}
