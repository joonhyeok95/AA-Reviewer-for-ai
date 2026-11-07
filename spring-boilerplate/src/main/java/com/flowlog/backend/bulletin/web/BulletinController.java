package com.flowlog.backend.bulletin.web;

import com.flowlog.backend.bulletin.domain.service.BulletinService;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinDetail;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinSaveRequest;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinSummary;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주보 API - 예배순서·교회소식·섬김위원·교리문답·전례문. (권한 bulletin.*)
 *
 * <p>작성 중(DRAFT) 주보는 등록·수정 권한이 있는 사용자만 목록·상세에서 본다.
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Logging
@Tag(name = "Bulletin", description = "주보 (예배순서·교회소식·섬김위원·교리문답·전례문, 이번 주 주보)")
@RestController
@RequestMapping("/api/v1/bulletins")
@RequiredArgsConstructor
public class BulletinController {

  /** 작성 중 주보까지 볼 수 있는 권한. */
  static final Set<String> EDITOR_AUTHORITIES =
      Set.of("SCOPE_bulletin.add", "SCOPE_bulletin.edit", "ROLE_SYS_ADMIN");

  private final BulletinService bulletinService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 주보 목록 (예배일 역순).
   *
   * @param pageable 페이징 (size 기본 20)
   * @param authentication 인증 (편집 권한이면 작성 중 포함)
   * @return 주보 목록
   */
  @Operation(summary = "주보 목록 (예배일 역순, 편집 권한이면 작성 중 포함)")
  @GetMapping
  public ResponseEntity<MessageResponse<PageResponse<BulletinSummary>>> getBulletins(
      @ParameterObject @PageableDefault(size = 20) Pageable pageable,
      Authentication authentication) {
    return messageHttpResponse.successPage(
        bulletinService.getBulletins(isEditor(authentication), pageable));
  }

  /**
   * 이번 주 주보. (오늘 ~ 6일 뒤 게시 주보, 없으면 가장 최근 게시 주보)
   *
   * @return 주보 상세
   */
  @Operation(summary = "이번 주 주보 (대시보드)")
  @GetMapping("/current")
  public ResponseEntity<MessageResponse<BulletinDetail>> getCurrent() {
    return messageHttpResponse.success(bulletinService.getCurrent());
  }

  /**
   * 주보 상세.
   *
   * @param id 주보 ID
   * @param authentication 인증
   * @return 주보 상세
   */
  @Operation(summary = "주보 상세 (교리문답·전례문 본문 포함)")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<BulletinDetail>> getBulletin(
      @PathVariable("id") @Positive Long id, Authentication authentication) {
    return messageHttpResponse.success(
        bulletinService.getBulletin(id, isEditor(authentication)));
  }

  /**
   * 주보 등록.
   *
   * @param request 등록 요청
   * @return 등록된 주보 (201)
   */
  @Operation(summary = "주보 등록")
  @PostMapping
  public ResponseEntity<MessageResponse<BulletinDetail>> createBulletin(
      @Valid @RequestBody BulletinSaveRequest request) {
    return messageHttpResponse.success(bulletinService.createBulletin(request));
  }

  /**
   * 주보 수정. (하위 전체 교체)
   *
   * @param id 주보 ID
   * @param request 수정 요청
   * @return 수정된 주보
   */
  @Operation(summary = "주보 수정 (하위 전체 교체)")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<BulletinDetail>> updateBulletin(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody BulletinSaveRequest request) {
    return messageHttpResponse.success(bulletinService.updateBulletin(id, request));
  }

  /**
   * 주보 삭제.
   *
   * @param id 주보 ID
   * @return result 없음
   */
  @Operation(summary = "주보 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteBulletin(
      @PathVariable("id") @Positive Long id) {
    bulletinService.deleteBulletin(id);
    return messageHttpResponse.success();
  }

  private static boolean isEditor(Authentication authentication) {
    return authentication != null && authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority).anyMatch(EDITOR_AUTHORITIES::contains);
  }
}
