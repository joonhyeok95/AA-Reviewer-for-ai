package com.flowlog.backend.role.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한별 메뉴 접근. (TB_ROLE_MENU)
 *
 * <p>Keycloak scope 와 동일한 체계: list(조회) / add(등록) / edit(수정) / del(삭제)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_ROLE_MENU")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleMenu extends AssignedIdEntity<RoleMenuId> {

  @EmbeddedId
  private RoleMenuId id; // 권한ID + 메뉴ID

  @Column(name = "LIST_YN", length = 1, nullable = false)
  private String listYn; // 조회권한

  @Column(name = "ADD_YN", length = 1, nullable = false)
  private String addYn; // 등록권한

  @Column(name = "EDIT_YN", length = 1, nullable = false)
  private String editYn; // 수정권한

  @Column(name = "DEL_YN", length = 1, nullable = false)
  private String delYn; // 삭제권한

  @Builder
  private RoleMenu(
      Long roleId, Long menuId, String listYn, String addYn, String editYn, String delYn) {
    this.id = new RoleMenuId(roleId, menuId);
    this.listYn = listYn;
    this.addYn = addYn;
    this.editYn = editYn;
    this.delYn = delYn;
  }

  /**
   * 메뉴 권한을 변경한다.
   *
   * @param listYn 조회권한
   * @param addYn 등록권한
   * @param editYn 수정권한
   * @param delYn 삭제권한
   */
  public void changeAuthority(String listYn, String addYn, String editYn, String delYn) {
    this.listYn = listYn;
    this.addYn = addYn;
    this.editYn = editYn;
    this.delYn = delYn;
  }
}
