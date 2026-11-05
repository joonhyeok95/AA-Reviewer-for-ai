package com.flowlog.backend.role.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한. (TB_ROLE)
 *
 * <ul>
 *   <li>ROLE_LEVEL : 1 시스템관리자 / 2 모듈마스터 / 3 모듈매니저 / 4 일반사용자 (작을수록 상위)</li>
 *   <li>레벨은 "등급", 실제 가능한 행위는 {@link RoleMenu} (list/add/edit/del) 로 표현한다.
 *       예) 조회 전용 매니저 = 레벨 3 + 메뉴권한 LIST 만 부여</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_ROLE")
@SequenceGenerator(name = "SQ_ROLE_GEN", sequenceName = "SQ_ROLE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

  /** 시스템관리자 레벨. */
  public static final int LEVEL_SYSTEM_ADMIN = 1;

  /** 모듈마스터 레벨. */
  public static final int LEVEL_MODULE_MASTER = 2;

  /** 모듈매니저 레벨. */
  public static final int LEVEL_MODULE_MANAGER = 3;

  /** 일반사용자 레벨. */
  public static final int LEVEL_GENERAL_USER = 4;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_ROLE_GEN")
  @Column(name = "ROLE_ID")
  private Long id; // 권한ID

  @Column(name = "ROLE_CD", length = 50, nullable = false, updatable = false)
  private String roleCd; // 권한코드

  @Column(name = "ROLE_TYPE_CD", length = 50, nullable = false)
  private String roleTypeCd; // 권한유형 (공통코드 ROLE_TYPE)

  @Column(name = "ROLE_LEVEL", nullable = false)
  private Integer roleLevel; // 권한레벨

  @Column(name = "MODULE_CD", length = 50)
  private String moduleCd; // 모듈코드 (공통코드 MODULE)

  @Column(name = "ROLE_DESC", length = 1000)
  private String roleDesc; // 설명

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "SYSTEM_YN", length = 1, nullable = false, updatable = false)
  private String systemYn; // 기본권한여부

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private Role(
      String roleCd, String roleTypeCd, Integer roleLevel, String moduleCd, String roleDesc,
      Integer sortOrder, String systemYn, String useYn) {
    this.roleCd = roleCd;
    this.roleTypeCd = roleTypeCd;
    this.roleLevel = roleLevel;
    this.moduleCd = moduleCd;
    this.roleDesc = roleDesc;
    this.sortOrder = sortOrder;
    this.systemYn = systemYn;
    this.useYn = useYn;
  }

  /**
   * 권한 정보를 수정한다. (권한코드, 기본권한여부는 변경 불가)
   *
   * @param roleTypeCd 권한유형
   * @param roleLevel 권한레벨
   * @param moduleCd 모듈코드
   * @param roleDesc 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public void update(
      String roleTypeCd, Integer roleLevel, String moduleCd, String roleDesc,
      Integer sortOrder, String useYn) {
    this.roleTypeCd = roleTypeCd;
    this.roleLevel = roleLevel;
    this.moduleCd = moduleCd;
    this.roleDesc = roleDesc;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }

  /**
   * 시스템관리자 권한 여부.
   *
   * @return 시스템관리자 레벨이면 true
   */
  public boolean isSystemAdmin() {
    return roleLevel != null && roleLevel == LEVEL_SYSTEM_ADMIN;
  }
}
