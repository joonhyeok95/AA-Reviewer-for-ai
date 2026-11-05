package com.flowlog.backend.menu.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import com.flowlog.backend.global.constant.YesNo;
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
 * 메뉴. (TB_MENU) - PARENT_MENU_ID 자기참조 계층형, 깊이 제한 없음.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_MENU")
@SequenceGenerator(name = "SQ_MENU_GEN", sequenceName = "SQ_MENU", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_MENU_GEN")
  @Column(name = "MENU_ID")
  private Long id; // 메뉴ID

  @Column(name = "PARENT_MENU_ID")
  private Long parentMenuId; // 상위메뉴ID (최상위 NULL)

  @Column(name = "MODULE_CD", length = 50)
  private String moduleCd; // 모듈코드 (공통코드 MODULE)

  @Column(name = "VIEW_TYPE_CD", length = 50, nullable = false)
  private String viewTypeCd; // 조회유형 (공통코드 MENU_VIEW_TYPE)

  @Column(name = "MENU_URL", length = 1000)
  private String menuUrl; // URL

  @Column(name = "PERM_CD", length = 100)
  private String permCd; // API 권한 리소스코드 (예: sample → SCOPE_sample.list 등)

  @Column(name = "NEW_WINDOW_YN", length = 1, nullable = false)
  private String newWindowYn; // 새창여부

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "ADMIN_MENU_YN", length = 1, nullable = false)
  private String adminMenuYn; // 관리자메뉴여부

  @Column(name = "SIDE_MENU_YN", length = 1, nullable = false)
  private String sideMenuYn; // 사이드메뉴여부 (상단 유틸리티)

  @Column(name = "DISPLAY_YN", length = 1, nullable = false)
  private String displayYn; // 노출여부

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private Menu(
      Long parentMenuId, String moduleCd, String viewTypeCd, String menuUrl, String permCd,
      String newWindowYn, Integer sortOrder, String adminMenuYn, String sideMenuYn,
      String displayYn, String useYn) {
    this.parentMenuId = parentMenuId;
    this.moduleCd = moduleCd;
    this.viewTypeCd = viewTypeCd;
    this.menuUrl = menuUrl;
    this.permCd = permCd;
    this.newWindowYn = newWindowYn;
    this.sortOrder = sortOrder;
    this.adminMenuYn = adminMenuYn;
    this.sideMenuYn = sideMenuYn;
    this.displayYn = displayYn;
    this.useYn = useYn;
  }

  /**
   * 메뉴 정보를 수정한다.
   *
   * @param moduleCd 모듈코드
   * @param viewTypeCd 조회유형
   * @param menuUrl URL
   * @param newWindowYn 새창여부
   * @param adminMenuYn 관리자메뉴여부
   * @param sideMenuYn 사이드메뉴여부
   * @param displayYn 노출여부
   * @param useYn 사용여부
   */
  public void update(
      String moduleCd, String viewTypeCd, String menuUrl, String newWindowYn,
      String adminMenuYn, String sideMenuYn, String displayYn, String useYn) {
    this.moduleCd = moduleCd;
    this.viewTypeCd = viewTypeCd;
    this.menuUrl = menuUrl;
    this.newWindowYn = newWindowYn;
    this.adminMenuYn = adminMenuYn;
    this.sideMenuYn = sideMenuYn;
    this.displayYn = displayYn;
    this.useYn = useYn;
  }

  /**
   * 메뉴 위치(상위메뉴, 정렬순서)를 변경한다.
   *
   * @param parentMenuId 상위메뉴ID (최상위 NULL)
   * @param sortOrder 정렬순서
   * @throws IllegalArgumentException 자기 자신을 상위메뉴로 지정한 경우
   */
  public void move(Long parentMenuId, Integer sortOrder) {
    if (id != null && id.equals(parentMenuId)) {
      throw new IllegalArgumentException("자기 자신을 상위메뉴로 지정할 수 없습니다.");
    }
    this.parentMenuId = parentMenuId;
    this.sortOrder = sortOrder;
  }

  /**
   * 최상위 메뉴 여부.
   *
   * @return 상위메뉴가 없으면 true
   */
  public boolean isRoot() {
    return parentMenuId == null;
  }

  /**
   * 관리자메뉴 여부.
   *
   * @return 관리자메뉴이면 true
   */
  public boolean isAdminMenu() {
    return YesNo.isYes(adminMenuYn);
  }
}
