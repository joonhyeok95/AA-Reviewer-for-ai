package com.flowlog.backend.menu.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메뉴 다국어. (TB_MENU_LANG)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_MENU_LANG")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuLang extends AssignedIdEntity<MenuLangId> {

  @EmbeddedId
  private MenuLangId id; // 메뉴ID + 언어코드

  @Column(name = "MENU_NM", length = 200, nullable = false)
  private String menuNm; // 메뉴명

  @Lob
  @Column(name = "MENU_CONTENT")
  private String menuContent; // 본문 HTML (조회유형 CONTENT)

  /**
   * 메뉴 다국어를 생성한다.
   *
   * @param menuId 메뉴ID
   * @param langCd 언어코드
   * @param menuNm 메뉴명
   * @param menuContent 본문 (CONTENT 유형이 아니면 null)
   */
  public MenuLang(Long menuId, String langCd, String menuNm, String menuContent) {
    this.id = new MenuLangId(menuId, langCd);
    this.menuNm = menuNm;
    this.menuContent = menuContent;
  }

  /**
   * 메뉴명/본문을 변경한다.
   *
   * @param menuNm 메뉴명
   * @param menuContent 본문
   */
  public void update(String menuNm, String menuContent) {
    this.menuNm = menuNm;
    this.menuContent = menuContent;
  }
}
