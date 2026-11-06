package com.flowlog.backend.menu.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메뉴 다국어 PK. (MENU_ID + LANG_CD)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuLangId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "MENU_ID")
  private Long menuId; // 메뉴ID

  @Column(name = "LANG_CD", length = 10)
  private String langCd; // 언어코드
}
