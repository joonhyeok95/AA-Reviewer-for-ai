package com.flowlog.backend.menu.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 사용자 메뉴 트리 노드.
 *
 * @param menuId 메뉴ID
 * @param menuNm 메뉴명 (요청 언어, 없으면 ko_KR)
 * @param moduleCd 모듈코드
 * @param viewTypeCd 조회유형 (MENU, LINK, CONTENT, IFRAME)
 * @param menuUrl URL
 * @param newWindow 새창여부
 * @param adminMenu 관리자메뉴여부
 * @param sideMenu 사이드메뉴여부 (상단 유틸리티)
 * @param permission 이 메뉴에서 가능한 작업 (사용자 유효 권한 합산)
 * @param children 하위 메뉴 (정렬순서)
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "사용자 메뉴 트리")
public record MenuTreeResponse(
    @Schema(description = "메뉴ID", example = "3") Long menuId,
    @Schema(description = "메뉴명", example = "거래목록") String menuNm,
    @Schema(description = "모듈코드", example = "TX") String moduleCd,
    @Schema(description = "조회유형", example = "MENU") String viewTypeCd,
    @Schema(description = "URL", example = "/tx/trades/list") String menuUrl,
    @Schema(description = "새창여부") boolean newWindow,
    @Schema(description = "관리자메뉴여부") boolean adminMenu,
    @Schema(description = "사이드메뉴여부") boolean sideMenu,
    @Schema(description = "작업 권한") MenuPermission permission,
    @Schema(description = "하위 메뉴") List<MenuTreeResponse> children) {

  /**
   * 메뉴 작업 권한.
   *
   * @param list 조회
   * @param add 등록
   * @param edit 수정
   * @param del 삭제
   */
  public record MenuPermission(boolean list, boolean add, boolean edit, boolean del) {

    /** 전체 권한 (시스템관리자). */
    public static final MenuPermission ALL = new MenuPermission(true, true, true, true);

    /**
     * 두 권한을 합친다. (여러 권한을 가진 사용자)
     *
     * @param other 다른 권한
     * @return 합산 권한
     */
    public MenuPermission merge(MenuPermission other) {
      return new MenuPermission(list || other.list, add || other.add, edit || other.edit,
          del || other.del);
    }
  }
}
