package com.flowlog.backend.role.domain.service.dto;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 권한 관리 DTO. (권한, 권한별 메뉴 접근, 사용자/부서 부여)
 *
 * @author flowlog
 * @since 2026-11-06
 */
public final class RoleAdminDtos {

  private RoleAdminDtos() {
  }

  /**
   * 권한 (관리 목록).
   *
   * @param roleId 권한ID
   * @param roleCd 권한코드
   * @param roleNm 권한명 (요청 언어, 없으면 기본 언어)
   * @param names 언어별 권한명
   * @param roleTypeCd 권한유형 (공통코드 ROLE_TYPE)
   * @param roleLevel 권한레벨 (1 시스템관리자 ~ 4 일반사용자)
   * @param moduleCd 모듈코드
   * @param roleDesc 설명
   * @param sortOrder 정렬순서
   * @param systemYn 기본 권한 여부 (삭제·유형/레벨 변경 불가)
   * @param useYn 사용여부
   * @param userGrantCnt 사용자 부여 건수
   * @param deptGrantCnt 부서 부여 건수
   * @param menuCnt 접근 메뉴 수
   */
  @Schema(description = "권한 (관리)")
  public record RoleAdminResponse(
      Long roleId, String roleCd, String roleNm, Map<String, String> names, String roleTypeCd,
      Integer roleLevel, String moduleCd, String roleDesc, Integer sortOrder, String systemYn,
      String useYn, long userGrantCnt, long deptGrantCnt, long menuCnt) {
  }

  /**
   * 권한 등록/수정 요청. (수정 시 roleCd 는 바꾸지 않음)
   *
   * @param roleCd 권한코드 (영문 대문자·숫자·_)
   * @param names 언어별 권한명
   * @param roleTypeCd 권한유형
   * @param roleLevel 권한레벨
   * @param moduleCd 모듈코드 (모듈 무관 null)
   * @param roleDesc 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  @Schema(description = "권한 등록/수정 요청")
  public record RoleSaveRequest(
      @Schema(description = "권한코드", example = "TX_AUDITOR")
      @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String roleCd,
      @Schema(description = "언어별 권한명") @NotEmpty
      Map<@NotBlank @Size(max = 10) String, @Size(max = 200) String> names,
      @Schema(description = "권한유형", example = "MODULE_MANAGER") @NotBlank @Size(max = 50)
      String roleTypeCd,
      @Schema(description = "권한레벨", example = "3") @NotNull @Min(1) @Max(4) Integer roleLevel,
      @Schema(description = "모듈코드", example = "TX") @Size(max = 50) String moduleCd,
      @Schema(description = "설명") @Size(max = 1000) String roleDesc,
      @Schema(description = "정렬순서", example = "20") @NotNull @Min(0) @Max(99999) Integer sortOrder,
      @NotBlank @Pattern(regexp = "^[YN]$") String useYn) {
  }

  /**
   * 권한별 메뉴 접근 트리 노드.
   *
   * @param menuId 메뉴ID
   * @param menuNm 메뉴명
   * @param viewTypeCd 조회유형
   * @param permCd API 권한 리소스코드 (있으면 체크한 작업이 API 권한이 됨)
   * @param useYn 메뉴 사용여부
   * @param displayYn 메뉴 노출여부
   * @param listYn 조회
   * @param addYn 등록
   * @param editYn 수정
   * @param delYn 삭제
   * @param children 하위 메뉴
   */
  @Schema(description = "권한별 메뉴 접근")
  public record RoleMenuNode(
      Long menuId, String menuNm, String viewTypeCd, String permCd, String useYn,
      String displayYn, String listYn, String addYn, String editYn, String delYn,
      List<RoleMenuNode> children) {
  }

  /**
   * 권한의 메뉴 접근.
   *
   * @param roleId 권한ID
   * @param roleCd 권한코드
   * @param allMenus 시스템관리자 권한 (모든 메뉴·작업 허용, 설정 불필요)
   * @param menus 메뉴 트리 (전체 메뉴 + 이 권한의 접근)
   */
  @Schema(description = "권한별 메뉴 접근 (트리)")
  public record RoleMenusResponse(
      Long roleId, String roleCd, boolean allMenus, List<RoleMenuNode> menus) {
  }

  /**
   * 메뉴 접근 항목.
   *
   * @param menuId 메뉴ID
   * @param listYn 조회
   * @param addYn 등록
   * @param editYn 수정
   * @param delYn 삭제
   */
  @Schema(description = "메뉴 접근 항목")
  public record RoleMenuItem(
      @NotNull @Positive Long menuId,
      @NotBlank @Pattern(regexp = "^[YN]$") String listYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String addYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String editYn,
      @NotBlank @Pattern(regexp = "^[YN]$") String delYn) {
  }

  /**
   * 권한별 메뉴 접근 저장 요청. (전체 교체 - 목록에 없는 메뉴는 접근 해제)
   *
   * @param items 메뉴 접근 목록
   */
  @Schema(description = "권한별 메뉴 접근 저장 (전체 교체)")
  public record RoleMenuSaveRequest(@NotNull @Size(max = 1000) List<@Valid RoleMenuItem> items) {
  }

  /**
   * 권한 부여 내역.
   *
   * @param grantId 부여ID
   * @param targetType USER / DEPT
   * @param targetId 사용자ID / 부서코드
   * @param targetNm 사용자명 / 부서명
   * @param targetSubNm 사용자 주부서명 / 부서 사업장명
   * @param startDt 시작일
   * @param endDt 종료일 (무기한 9999-12-31)
   * @param grantReason 부여사유
   * @param status ACTIVE(유효) / SCHEDULED(시작 전) / EXPIRED(만료)
   * @param updatedBy 최종 변경자
   * @param updatedAt 최종 변경일시
   */
  @Schema(description = "권한 부여 내역")
  public record RoleGrantResponse(
      Long grantId, String targetType, String targetId, String targetNm, String targetSubNm,
      LocalDate startDt, LocalDate endDt, String grantReason, String status, String updatedBy,
      LocalDateTime updatedAt) {
  }

  /**
   * 권한의 부여 내역.
   *
   * @param roleId 권한ID
   * @param users 사용자 부여
   * @param depts 부서 부여 (소속 사용자 전원에게 적용)
   */
  @Schema(description = "권한 부여 내역 (사용자/부서)")
  public record RoleGrantsResponse(
      Long roleId, List<RoleGrantResponse> users, List<RoleGrantResponse> depts) {
  }

  /**
   * 권한 부여 변경 이력.
   *
   * @param histId 이력ID
   * @param actionCd GRANT 부여 / UPDATE 수정 / REVOKE 회수
   * @param targetType USER / DEPT
   * @param targetId 사용자ID / 부서코드
   * @param targetNm 사용자명 / 부서명 (표시 언어, 삭제된 대상은 null)
   * @param startDt 시작일 (변경 후, 회수는 회수 전)
   * @param endDt 종료일
   * @param reason 사유
   * @param changedBy 변경자ID
   * @param changedByNm 변경자명
   * @param changedAt 변경일시
   */
  @Schema(description = "권한 부여 변경 이력")
  public record RoleGrantHistResponse(
      Long histId, String actionCd, String targetType, String targetId, String targetNm,
      LocalDate startDt, LocalDate endDt, String reason, String changedBy, String changedByNm,
      LocalDateTime changedAt) {
  }

  /**
   * 권한 부여 등록/수정 요청. (수정 시 대상은 바꾸지 않음 - 기간·사유만)
   *
   * @param targetType USER / DEPT
   * @param targetId 사용자ID / 부서코드
   * @param startDt 시작일
   * @param endDt 종료일 (비우면 무기한)
   * @param grantReason 부여사유
   */
  @Schema(description = "권한 부여 등록/수정 요청")
  public record RoleGrantSaveRequest(
      @Schema(description = "대상 유형", example = "USER") @NotBlank
      @Pattern(regexp = "^(USER|DEPT)$") String targetType,
      @Schema(description = "사용자ID / 부서코드", example = "emp01") @NotBlank @Size(max = 50)
      String targetId,
      @Schema(description = "시작일", example = "2026-11-06") @NotNull LocalDate startDt,
      @Schema(description = "종료일 (비우면 무기한)", example = "2026-12-31") LocalDate endDt,
      @Schema(description = "부여사유") @Size(max = 1000) String grantReason) {
  }
}
