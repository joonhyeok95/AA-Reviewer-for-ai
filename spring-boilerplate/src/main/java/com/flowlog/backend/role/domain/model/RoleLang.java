package com.flowlog.backend.role.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 다국어. (TB_ROLE_LANG)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_ROLE_LANG")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleLang extends AssignedIdEntity<RoleLangId> {

  @EmbeddedId
  private RoleLangId id; // 권한ID + 언어코드

  @Column(name = "ROLE_NM", length = 200, nullable = false)
  private String roleNm; // 권한명

  /**
   * 권한 다국어를 생성한다.
   *
   * @param roleId 권한ID
   * @param langCd 언어코드
   * @param roleNm 권한명
   */
  public RoleLang(Long roleId, String langCd, String roleNm) {
    this.id = new RoleLangId(roleId, langCd);
    this.roleNm = roleNm;
  }

  /**
   * 권한명을 변경한다.
   *
   * @param roleNm 권한명
   */
  public void changeName(String roleNm) {
    this.roleNm = roleNm;
  }
}
