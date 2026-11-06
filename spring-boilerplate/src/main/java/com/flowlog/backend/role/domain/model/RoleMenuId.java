package com.flowlog.backend.role.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한별 메뉴 PK. (ROLE_ID + MENU_ID)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleMenuId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "ROLE_ID")
  private Long roleId; // 권한ID

  @Column(name = "MENU_ID")
  private Long menuId; // 메뉴ID
}
