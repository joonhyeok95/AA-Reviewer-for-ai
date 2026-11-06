package com.flowlog.backend.code.web;

import com.flowlog.backend.code.domain.service.CodeAdminService;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeAdminResponse;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeGroupAdminResponse;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeGroupSaveRequest;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeSaveRequest;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통코드 관리 API. (권한 code.list / add / edit / del)
 *
 * <p>사용자 화면의 코드 조회는 {@code GET /api/v1/codes/{groupCd}} (사용 중 코드, 사용자 언어 이름).
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Tag(name = "Code Admin", description = "공통코드 관리 (그룹·코드·언어별 이름)")
@Validated
@RestController
@RequestMapping("/api/v1/code-groups")
@RequiredArgsConstructor
public class CodeAdminController {

  private final CodeAdminService codeAdminService;
  private final LangService langService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 전체 그룹 목록.
   *
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 그룹 목록
   */
  @Operation(summary = "코드 그룹 목록", description = "미사용 그룹 포함, 언어별 그룹명, 코드 수")
  @GetMapping
  public ResponseEntity<MessageResponse<List<CodeGroupAdminResponse>>> getGroups(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        codeAdminService.getGroups(langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 그룹 등록.
   *
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 등록된 그룹
   */
  @Operation(summary = "코드 그룹 등록", description = "기본 언어 그룹명 필수")
  @PostMapping
  public ResponseEntity<MessageResponse<CodeGroupAdminResponse>> createGroup(
      @Valid @RequestBody CodeGroupSaveRequest request, Authentication authentication,
      Locale locale) {
    return messageHttpResponse.success(codeAdminService.createGroup(request,
        langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 그룹 수정.
   *
   * @param groupCd 그룹코드
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 수정된 그룹
   */
  @Operation(summary = "코드 그룹 수정", description = "그룹코드는 바꾸지 않습니다.")
  @PutMapping("/{groupCd}")
  public ResponseEntity<MessageResponse<CodeGroupAdminResponse>> updateGroup(
      @Parameter(description = "그룹코드", example = "NOTICE_TAG")
      @PathVariable("groupCd") @Size(max = 50) String groupCd,
      @Valid @RequestBody CodeGroupSaveRequest request, Authentication authentication,
      Locale locale) {
    return messageHttpResponse.success(codeAdminService.updateGroup(groupCd, request,
        langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 그룹 삭제.
   *
   * @param groupCd 그룹코드
   * @return result 없음
   */
  @Operation(summary = "코드 그룹 삭제", description = "코드 포함 삭제. 시스템 그룹은 삭제할 수 없습니다.")
  @DeleteMapping("/{groupCd}")
  public ResponseEntity<MessageResponse<Void>> deleteGroup(
      @PathVariable("groupCd") @Size(max = 50) String groupCd) {
    codeAdminService.deleteGroup(groupCd);
    return messageHttpResponse.success();
  }

  /**
   * 그룹의 전체 코드.
   *
   * @param groupCd 그룹코드
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 코드 목록
   */
  @Operation(summary = "코드 목록 (관리)", description = "미사용 코드 포함, 언어별 코드명")
  @GetMapping("/{groupCd}/codes")
  public ResponseEntity<MessageResponse<List<CodeAdminResponse>>> getCodes(
      @PathVariable("groupCd") @Size(max = 50) String groupCd, Authentication authentication,
      Locale locale) {
    return messageHttpResponse.success(codeAdminService.getCodes(groupCd,
        langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 코드 등록.
   *
   * @param groupCd 그룹코드
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 등록된 코드
   */
  @Operation(summary = "코드 등록")
  @PostMapping("/{groupCd}/codes")
  public ResponseEntity<MessageResponse<CodeAdminResponse>> createCode(
      @PathVariable("groupCd") @Size(max = 50) String groupCd,
      @Valid @RequestBody CodeSaveRequest request, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(codeAdminService.createCode(groupCd, request,
        langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 코드 수정.
   *
   * @param groupCd 그룹코드
   * @param code 코드
   * @param request 요청
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 수정된 코드
   */
  @Operation(summary = "코드 수정", description = "코드값은 바꾸지 않습니다.")
  @PutMapping("/{groupCd}/codes/{code}")
  public ResponseEntity<MessageResponse<CodeAdminResponse>> updateCode(
      @PathVariable("groupCd") @Size(max = 50) String groupCd,
      @PathVariable("code") @Size(max = 50) String code,
      @Valid @RequestBody CodeSaveRequest request, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(codeAdminService.updateCode(groupCd, code, request,
        langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 코드 삭제.
   *
   * @param groupCd 그룹코드
   * @param code 코드
   * @return result 없음
   */
  @Operation(summary = "코드 삭제", description = "시스템 그룹의 코드는 삭제할 수 없습니다. (사용 여부로 관리)")
  @DeleteMapping("/{groupCd}/codes/{code}")
  public ResponseEntity<MessageResponse<Void>> deleteCode(
      @PathVariable("groupCd") @Size(max = 50) String groupCd,
      @PathVariable("code") @Size(max = 50) String code) {
    codeAdminService.deleteCode(groupCd, code);
    return messageHttpResponse.success();
  }
}
