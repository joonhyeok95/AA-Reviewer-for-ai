package com.flowlog.backend.user.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 소속부서 PK. (USER_ID + DEPT_CD)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeptId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "USER_ID", length = 50)
  private String userId; // 사용자ID

  @Column(name = "DEPT_CD", length = 50)
  private String deptCd; // 부서코드
}
