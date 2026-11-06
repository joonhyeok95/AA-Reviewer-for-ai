package com.flowlog.backend.role.web;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.role.domain.service.RoleAdminService;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleAdminResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleGrantSaveRequest;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleGrantsResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleMenuSaveRequest;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleMenusResponse;
import com.flowlog.backend.role.domain.service.dto.RoleAdminDtos.RoleSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 권한 관리 API. (권한 role.list / add / edit / del)
 *
 * <ul>
 *   <li>권한별 메뉴 접근 저장, 사용자/부서 부여·기간 변경·회수는 role.edit</li>
 *   <li>시스템관리자 권한(레벨 1)의 변경·부여는 시스템관리자만 (B6004)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Tag(name = "Role Admin", description = "권한 관리 (권한·메뉴 접근·사용자/부서 부여)")
@Validated
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleAdminController {

  private static final String SYS_ADMIN = "ROLE_SYS_ADMIN";
  private static final String TARGET_TYPE = "^(USER|DEPT)$";

  private final RoleAdminService roleAdminService;
  private final LangService langService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 전체 권한 목록.
   *
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 권한 목록
   */
  @Operation(summary = "권한 목록", description = "미사용 포함, 언어별 권한명, 부여·메뉴 수")
  @GetMapping
  public ResponseEntity<MessageResponse<List<RoleAdminResponse>>> getRoles(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(roleAdminService.getRoles(lang(authentication, locale)));
  }

  /**
   * 권한 등록.
   *
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 등록된 권한
   */
  @Operation(summary = "권한 등록", description = "레벨 1(시스템관리자) 권한은 시스템관리자만 등록")
  @PostMapping
  public ResponseEntity<MessageResponse<RoleAdminResponse>> createRole(
      @Valid @RequestBody RoleSaveRequest request, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(roleAdminService.createRole(request,
        isSysAdmin(authentication), lang(authentication, locale)));
  }

  /**
   * 권한 수정.
   *
   * @param id 권한ID
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 수정된 권한
   */
  @Operation(summary = "권한 수정", description = "권한코드는 바꾸지 않습니다. 기본 권한은 유형·레벨 변경 불가")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<RoleAdminResponse>> updateRole(
      @Parameter(description = "권한ID", example = "6") @PathVariable("id") @Positive Long id,
      @Valid @RequestBody RoleSaveRequest request, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(roleAdminService.updateRole(id, request,
        isSysAdmin(authentication), lang(authentication, locale)));
  }

  /**
   * 권한 삭제.
   *
   * @param id 권한ID
   * @param authentication 인증
   * @return result 없음
   */
  @Operation(summary = "권한 삭제", description = "기본 권한, 부여 내역이 있는 권한은 삭제 불가")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteRole(
      @PathVariable("id") @Positive Long id, Authentication authentication) {
    roleAdminService.deleteRole(id, isSysAdmin(authentication));
    return messageHttpResponse.success();
  }

  /**
   * 권한의 메뉴 접근.
   *
   * @param id 권한ID
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 메뉴 접근 트리
   */
  @Operation(summary = "권한별 메뉴 접근", description = "전체 메뉴 트리 + 이 권한의 조회/등록/수정/삭제")
  @GetMapping("/{id}/menus")
  public ResponseEntity<MessageResponse<RoleMenusResponse>> getRoleMenus(
      @PathVariable("id") @Positive Long id, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        roleAdminService.getRoleMenus(id, lang(authentication, locale)));
  }

  /**
   * 권한의 메뉴 접근 저장. (전체 교체)
   *
   * @param id 권한ID
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 저장 결과
   */
  @Operation(summary = "권한별 메뉴 접근 저장",
      description = "전체 교체. 등록/수정/삭제가 있으면 조회 포함, 상위 메뉴는 조회 자동 허용. 즉시 반영(권한 캐시 삭제)")
  @PutMapping("/{id}/menus")
  public ResponseEntity<MessageResponse<RoleMenusResponse>> saveRoleMenus(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody RoleMenuSaveRequest request,
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(roleAdminService.saveRoleMenus(id, request,
        isSysAdmin(authentication), lang(authentication, locale)));
  }

  /**
   * 권한의 사용자/부서 부여 내역.
   *
   * @param id 권한ID
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 부여 내역
   */
  @Operation(summary = "권한 부여 내역", description = "사용자·부서별 기간, 상태(유효/시작 전/만료)")
  @GetMapping("/{id}/grants")
  public ResponseEntity<MessageResponse<RoleGrantsResponse>> getGrants(
      @PathVariable("id") @Positive Long id, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(roleAdminService.getGrants(id, lang(authentication, locale)));
  }

  /**
   * 권한 부여.
   *
   * @param id 권한ID
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 부여 내역
   */
  @Operation(summary = "권한 부여", description = "사용자 또는 부서(소속 전원). 같은 대상의 기간 중복 불가")
  @PostMapping("/{id}/grants")
  public ResponseEntity<MessageResponse<RoleGrantsResponse>> createGrant(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody RoleGrantSaveRequest request,
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(roleAdminService.createGrant(id, request,
        isSysAdmin(authentication), lang(authentication, locale)));
  }

  /**
   * 부여 기간·사유 변경.
   *
   * @param id 권한ID
   * @param targetType USER / DEPT
   * @param grantId 부여ID
   * @param request 요청 (대상은 무시)
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 부여 내역
   */
  @Operation(summary = "부여 기간·사유 변경")
  @PutMapping("/{id}/grants/{targetType}/{grantId}")
  public ResponseEntity<MessageResponse<RoleGrantsResponse>> updateGrant(
      @PathVariable("id") @Positive Long id,
      @PathVariable("targetType") @Pattern(regexp = TARGET_TYPE) String targetType,
      @PathVariable("grantId") @Positive Long grantId,
      @Valid @RequestBody RoleGrantSaveRequest request, Authentication authentication,
      Locale locale) {
    return messageHttpResponse.success(roleAdminService.updateGrant(id, targetType, grantId,
        request, isSysAdmin(authentication), lang(authentication, locale)));
  }

  /**
   * 부여 회수.
   *
   * @param id 권한ID
   * @param targetType USER / DEPT
   * @param grantId 부여ID
   * @param authentication 인증
   * @return result 없음
   */
  @Operation(summary = "부여 회수", description = "즉시 삭제. 다음 요청부터 권한이 빠진다")
  @DeleteMapping("/{id}/grants/{targetType}/{grantId}")
  public ResponseEntity<MessageResponse<Void>> deleteGrant(
      @PathVariable("id") @Positive Long id,
      @PathVariable("targetType") @Pattern(regexp = TARGET_TYPE) String targetType,
      @PathVariable("grantId") @Positive Long grantId, Authentication authentication) {
    roleAdminService.deleteGrant(id, targetType, grantId, isSysAdmin(authentication));
    return messageHttpResponse.success();
  }

  private String lang(Authentication authentication, Locale locale) {
    return langService.resolveUserLang(authentication, locale);
  }

  private static boolean isSysAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(SYS_ADMIN::equals);
  }
}
