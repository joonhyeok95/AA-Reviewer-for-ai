package com.flowlog.backend.menu.web;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.menu.domain.service.MenuAdminService;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuAdminNode;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuDetailResponse;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuOrderRequest;
import com.flowlog.backend.menu.domain.service.dto.MenuAdminDtos.MenuSaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
 * 메뉴 관리 API. (권한 menu.list / add / edit / del)
 *
 * <p>사용자 메뉴는 {@code GET /api/v1/menus/me}, 본문 메뉴 내용은 {@code GET /api/v1/menus/{id}/content}.
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Tag(name = "Menu Admin", description = "메뉴 관리 (트리·언어별 이름·본문·순서)")
@Validated
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuAdminController {

  private final MenuAdminService menuAdminService;
  private final LangService langService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 전체 메뉴 트리.
   *
   * @param authentication 인증
   * @param locale 요청 Locale
   * @return 메뉴 트리
   */
  @Operation(summary = "메뉴 트리 (관리)", description = "미사용·미노출 메뉴 포함")
  @GetMapping
  public ResponseEntity<MessageResponse<List<MenuAdminNode>>> getTree(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        menuAdminService.getTree(langService.resolveUserLang(authentication, locale)));
  }

  /**
   * 메뉴 상세.
   *
   * @param id 메뉴ID
   * @return 상세
   */
  @Operation(summary = "메뉴 상세", description = "언어별 메뉴명·본문, 접근 권한 수, 하위 메뉴 수")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<MenuDetailResponse>> getMenu(
      @Parameter(description = "메뉴ID", example = "12") @PathVariable("id") @Positive Long id) {
    return messageHttpResponse.success(menuAdminService.getMenu(id));
  }

  /**
   * 메뉴 등록.
   *
   * @param request 요청
   * @return 등록된 메뉴
   */
  @Operation(summary = "메뉴 등록", description = "유형별 URL 검증, 기본 언어 메뉴명 필수, 본문은 정화 후 저장")
  @PostMapping
  public ResponseEntity<MessageResponse<MenuDetailResponse>> createMenu(
      @Valid @RequestBody MenuSaveRequest request) {
    return messageHttpResponse.success(menuAdminService.createMenu(request));
  }

  /**
   * 메뉴 순서 변경.
   *
   * @param request 요청
   * @return result 없음
   */
  @Operation(summary = "메뉴 순서 변경", description = "같은 상위 메뉴 안에서 목록 순서대로 정렬순서 재지정")
  @PutMapping("/order")
  public ResponseEntity<MessageResponse<Void>> reorder(
      @Valid @RequestBody MenuOrderRequest request) {
    menuAdminService.reorder(request);
    return messageHttpResponse.success();
  }

  /**
   * 메뉴 수정.
   *
   * @param id 메뉴ID
   * @param request 요청
   * @return 수정된 메뉴
   */
  @Operation(summary = "메뉴 수정", description = "상위 메뉴 이동 포함 (자기 자신/하위 메뉴 불가)")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<MenuDetailResponse>> updateMenu(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody MenuSaveRequest request) {
    return messageHttpResponse.success(menuAdminService.updateMenu(id, request));
  }

  /**
   * 메뉴 삭제.
   *
   * @param id 메뉴ID
   * @return result 없음
   */
  @Operation(summary = "메뉴 삭제", description = "하위 메뉴가 있으면 삭제 불가. 권한별 메뉴 접근도 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteMenu(@PathVariable("id") @Positive Long id) {
    menuAdminService.deleteMenu(id);
    return messageHttpResponse.success();
  }
}
