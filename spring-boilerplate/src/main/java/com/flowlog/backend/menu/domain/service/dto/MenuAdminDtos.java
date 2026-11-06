package com.flowlog.backend.menu.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 메뉴 관리 DTO.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public final class MenuAdminDtos {

  private MenuAdminDtos() {
  }

  /**
   * 메뉴 트리 노드 (관리 - 미사용/미노출 포함).
   *
   * @param menuId 메뉴ID
   * @param parentMenuId 상위메뉴ID
   * @param menuNm 메뉴명 (요청 언어, 없으면 기본 언어)
   * @param moduleCd 모듈코드
   * @param viewTypeCd 조회유형 (MENU, LINK, CONTENT, IFRAME)
   * @param menuUrl URL
   * @param permCd API 권한 리소스코드
   * @param sortOrder 정렬순서
   * @param newWindowYn 새창여부
   * @param adminMenuYn 관리자메뉴여부
   * @param sideMenuYn 사이드메뉴여부
   * @param displayYn 노출여부
   * @param useYn 사용여부
   * @param children 하위 메뉴
   */
  @Schema(description = "메뉴 트리 (관리)")
  public record MenuAdminNode(
      Long menuId, Long parentMenuId, String menuNm, String moduleCd, String viewTypeCd,
      String menuUrl, String permCd, Integer sortOrder, String newWindowYn, String adminMenuYn,
      String sideMenuYn, String displayYn, String useYn, List<MenuAdminNode> children) {
  }

  /**
   * 언어별 메뉴명·본문.
   *
   * @param langCd 언어코드
   * @param menuNm 메뉴명
   * @param menuContent 본문 HTML (CONTENT 유형)
   */
  @Schema(description = "언어별 메뉴명·본문")
  public record MenuI18n(
      @Schema(description = "언어", example = "ko") @NotBlank @Size(max = 10) String langCd,
      @Schema(description = "메뉴명", example = "공지사항") @Size(max = 200) String menuNm,
      @Schema(description = "본문 HTML (CONTENT 유형만)") @Size(max = 1_000_000) String menuContent) {
  }

  /**
   * 메뉴 상세 (수정 화면).
   *
   * @param menuId 메뉴ID
   * @param parentMenuId 상위메뉴ID
   * @param moduleCd 모듈코드
   * @param viewTypeCd 조회유형
   * @param menuUrl URL
   * @param permCd API 권한 리소스코드
   * @param sortOrder 정렬순서
   * @param newWindowYn 새창여부
   * @param adminMenuYn 관리자메뉴여부
   * @param sideMenuYn 사이드메뉴여부
   * @param displayYn 노출여부
   * @param useYn 사용여부
   * @param i18n 언어별 메뉴명·본문
   * @param roleCnt 접근 권한이 설정된 권한 수
   * @param childCnt 하위 메뉴 수
   */
  @Schema(description = "메뉴 상세 (관리)")
  public record MenuDetailResponse(
      Long menuId, Long parentMenuId, String moduleCd, String viewTypeCd, String menuUrl,
      String permCd, Integer sortOrder, String newWindowYn, String adminMenuYn, String sideMenuYn,
      String displayYn, String useYn, List<MenuI18n> i18n, int roleCnt, int childCnt) {
  }

  /**
   * 메뉴 등록/수정 요청.
   *
   * @param parentMenuId 상위메뉴ID (최상위 null)
   * @param moduleCd 모듈코드 (공통코드 MODULE)
   * @param viewTypeCd 조회유형 (공통코드 MENU_VIEW_TYPE)
   * @param menuUrl URL (MENU: /경로, LINK·IFRAME: http(s), CONTENT: 없음)
   * @param permCd API 권한 리소스코드 (예: notice → notice.list/add/edit/del)
   * @param sortOrder 정렬순서
   * @param newWindowYn 새창여부
   * @param adminMenuYn 관리자메뉴여부
   * @param sideMenuYn 사이드메뉴여부
   * @param displayYn 노출여부
   * @param useYn 사용여부
   * @param i18n 언어별 메뉴명·본문 (기본 언어 메뉴명 필수)
   */
  @Schema(description = "메뉴 등록/수정 요청")
  public record MenuSaveRequest(
      @Schema(description = "상위메뉴ID (최상위 null)", example = "11") @Positive Long parentMenuId,
      @Schema(description = "모듈코드", example = "TX") @Size(max = 50) String moduleCd,
      @Schema(description = "조회유형", example = "MENU") @NotBlank @Size(max = 50) String viewTypeCd,
      @Schema(description = "URL", example = "/system/codes") @Size(max = 1000) String menuUrl,
      @Schema(description = "API 권한 리소스코드", example = "code")
      @Pattern(regexp = "^[a-z][a-z0-9-]{0,49}$") String permCd,
      @Schema(description = "정렬순서", example = "1") @NotNull @Min(0) @Max(99999) Integer sortOrder,
      @NotBlank @Pattern(regexp = "^[YN]$") String newWindowYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String adminMenuYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String sideMenuYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String displayYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String useYn,
      @Schema(description = "언어별 메뉴명·본문") @NotEmpty @Size(max = 20) List<@Valid MenuI18n> i18n) {
  }

  /**
   * 같은 상위 메뉴 안의 순서 변경 요청.
   *
   * @param parentMenuId 상위메뉴ID (최상위 null)
   * @param menuIds 새 순서의 메뉴ID 목록
   */
  @Schema(description = "메뉴 순서 변경 요청")
  public record MenuOrderRequest(
      @Schema(description = "상위메뉴ID (최상위 null)") @Positive Long parentMenuId,
      @Schema(description = "새 순서의 메뉴ID") @NotEmpty @Size(max = 500)
      List<@NotNull @Positive Long> menuIds) {
  }

  /**
   * 본문형(CONTENT) 메뉴 본문 (사용자 화면).
   *
   * @param menuId 메뉴ID
   * @param menuNm 메뉴명
   * @param langCd 표시 언어
   * @param fallback 요청 언어 본문이 없어 기본 언어 본문을 표시
   * @param contentHtml 본문 HTML (저장 시 정화됨)
   */
  @Schema(description = "본문 메뉴 내용")
  public record MenuContentResponse(
      Long menuId, String menuNm, String langCd, boolean fallback, String contentHtml) {
  }
}
