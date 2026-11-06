package com.flowlog.backend.user.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 소속부서. (TB_USER_DEPT) - 사용자는 N개 부서에 소속될 수 있다. (겸직)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_USER_DEPT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDept extends AssignedIdEntity<UserDeptId> {

  @EmbeddedId
  private UserDeptId id; // 사용자ID + 부서코드

  @Column(name = "MAIN_DEPT_YN", length = 1, nullable = false)
  private String mainDeptYn; // 주부서여부

  /**
   * 사용자 소속부서를 생성한다.
   *
   * @param userId 사용자ID
   * @param deptCd 부서코드
   * @param mainDeptYn 주부서여부
   */
  public UserDept(String userId, String deptCd, String mainDeptYn) {
    this.id = new UserDeptId(userId, deptCd);
    this.mainDeptYn = mainDeptYn;
  }

  /**
   * 주부서여부를 변경한다.
   *
   * @param mainDeptYn 주부서여부
   */
  public void changeMainDept(String mainDeptYn) {
    this.mainDeptYn = mainDeptYn;
  }
}
