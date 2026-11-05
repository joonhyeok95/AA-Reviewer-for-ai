package com.flowlog.backend.menu.web;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.global.security.AuthenticationSubjects;
import com.flowlog.backend.menu.domain.service.MenuService;
import com.flowlog.backend.menu.domain.service.dto.MenuTreeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 API.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Tag(name = "Menu", description = "메뉴")
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

  private final MenuService menuService;
  private final MessageHttpResponse messageHttpResponse;
  private final LangService langService;

  /**
   * 로그인 사용자 메뉴 트리.
   *
   * @param authentication 인증 객체
   * @param locale 요청 언어 (Accept-Language, 사용자 설정 언어가 없을 때)
   * @return 메뉴 트리
   */
  @Operation(
      summary = "내 메뉴 트리 조회",
      description = "유효 권한(직접+부서, 기간 내)의 메뉴를 트리로 반환합니다. "
          + "메뉴명은 사용자 설정 언어 → Accept-Language → 기본 언어, 메뉴별 작업 권한(list/add/edit/del) 포함.")
  @GetMapping("/me")
  public ResponseEntity<MessageResponse<List<MenuTreeResponse>>> getMyMenus(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        menuService.getMyMenus(AuthenticationSubjects.subjectOf(authentication),
            langService.resolveUserLang(authentication, locale)));
  }
}
