package com.flowlog.backend.user.web;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.global.security.AuthenticationSubjects;
import com.flowlog.backend.user.domain.service.OrgService;
import com.flowlog.backend.user.domain.service.UserQueryService;
import com.flowlog.backend.user.domain.service.UserSettingService;
import com.flowlog.backend.user.domain.service.dto.OrgTreeResponse;
import com.flowlog.backend.user.domain.service.dto.UserLangResponse;
import com.flowlog.backend.user.domain.service.dto.UserLangUpdateRequest;
import com.flowlog.backend.user.domain.service.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 설정 / 조직도(인사 마스터) API.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Tag(name = "User", description = "사용자 설정 / 조직도 / 사용자 검색")
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

  private final UserSettingService userSettingService;
  private final OrgService orgService;
  private final UserQueryService userQueryService;
  private final LangService langService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 사용자 설정 언어 변경.
   *
   * @param authentication 인증 객체
   * @param request 변경 요청
   * @return 변경된 언어
   */
  @Operation(summary = "내 언어 설정 변경",
      description = "화면 표시·공지 조회·메일 수신 언어. 공통코드 LANG(사용 중)만 가능합니다.")
  @PutMapping("/users/me/lang")
  public ResponseEntity<MessageResponse<UserLangResponse>> changeMyLang(
      Authentication authentication, @Valid @RequestBody UserLangUpdateRequest request) {
    return messageHttpResponse.success(userSettingService.changeLang(
        AuthenticationSubjects.subjectOf(authentication), request.langCd()));
  }

  /**
   * 조직도 트리. (사업장 → 부서, 인사 마스터)
   *
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 조직도
   */
  @Operation(summary = "조직도 트리 조회",
      description = "인사 마스터의 사업장/부서 트리. 명칭은 사용자 언어(ko: 국문, 그 외: 영문명)")
  @GetMapping("/org/tree")
  public ResponseEntity<MessageResponse<List<OrgTreeResponse>>> getOrgTree(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        orgService.getOrgTree(langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 사용자 검색. (권한 부여 대상 선택 - role.list)
   *
   * @param keyword 사용자ID·이름 일부
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 사용자 목록 (최대 20건)
   */
  @Operation(summary = "사용자 검색",
      description = "사용자ID·이름 부분 일치, 사용 중인 사용자, 최대 20건. 권한 부여 대상 선택용")
  @GetMapping("/users")
  public ResponseEntity<MessageResponse<List<UserSummaryResponse>>> searchUsers(
      @Parameter(description = "검색어", example = "emp") @RequestParam("keyword") @Size(max = 50)
      String keyword, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        userQueryService.search(keyword, langService.resolveUserLang(authentication, locale)));
  }
}
