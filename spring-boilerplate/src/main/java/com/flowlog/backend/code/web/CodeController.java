package com.flowlog.backend.code.web;

import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.code.domain.service.dto.CodeResponse;
import com.flowlog.backend.code.domain.service.dto.LangResponse;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통코드 / 지원 언어 API. (화면 선택 목록용)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Validated
@Tag(name = "Code", description = "공통코드 / 지원 언어")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CodeController {

  private final CodeService codeService;
  private final LangService langService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 지원 언어 목록. (공통코드 LANG, USE_YN='Y')
   *
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 언어 목록
   */
  @Operation(summary = "지원 언어 목록",
      description = "언어 탭·언어 선택 목록·에디터 언어팩 매핑(editorLocale = LANG.ATTR1)의 기준. "
          + "언어명은 사용자 언어로 표시합니다.")
  @GetMapping("/langs")
  public ResponseEntity<MessageResponse<List<LangResponse>>> getLangs(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        langService.getLangs(langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 공통코드 목록. (사용 중, 사용자 언어 코드명)
   *
   * @param groupCd 그룹코드
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 코드 목록
   */
  @Operation(summary = "공통코드 목록", description = "예: NOTICE_CATEGORY, NOTICE_TAG, MAIL_SEND_TYPE")
  @GetMapping("/codes/{groupCd}")
  public ResponseEntity<MessageResponse<List<CodeResponse>>> getCodes(
      @Parameter(description = "그룹코드", example = "NOTICE_CATEGORY")
      @PathVariable("groupCd") @Pattern(regexp = "^[A-Z0-9_]{1,50}$") String groupCd,
      Authentication authentication, Locale locale) {
    String langCd = langService.resolveUserLang(authentication, locale);
    String defaultLang = langService.defaultLang();
    return messageHttpResponse.success(codeService.getCodeItems(groupCd).stream()
        .map(item -> new CodeResponse(item.code(), item.nameOf(langCd, defaultLang),
            item.sortOrder(), item.attr1()))
        .toList());
  }
}
