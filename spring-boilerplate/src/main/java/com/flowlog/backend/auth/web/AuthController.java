package com.flowlog.backend.auth.web;

import com.flowlog.backend.auth.domain.service.AuthService;
import com.flowlog.backend.auth.domain.service.dto.AuthUserResponse;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API.
 *
 * <ul>
 *   <li>OAuth2 BFF : 브라우저로 {@code /oauth2/authorization/keycloak} 접속 → 로그인 후 이 API 로 이동
 *       (클라이언트는 SESSION 쿠키만 가짐)</li>
 *   <li>SAML2 : 브라우저로 {@code /saml2/authenticate/keycloak} 접속 → 로그인 후 이 API 로 이동</li>
 *   <li>서버 간 : Client Credentials 토큰을 {@code Authorization: Bearer} 로 호출</li>
 *   <li>로그아웃(세션) : {@code POST /logout} (X-XSRF-TOKEN 헤더 필요) → Redis 세션 + Keycloak SSO 종료</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Tag(name = "Auth", description = "인증 (Keycloak OAuth2 / SAML2)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 현재 로그인 사용자 조회.
   *
   * @param authentication 인증 객체
   * @return 로그인 사용자 정보
   */
  @Operation(
      summary = "현재 로그인 사용자 조회",
      description = "인증 방식(OAUTH2/SAML2), 사용자ID, DB 유효 권한, Spring Security 권한을 반환합니다.")
  @GetMapping("/me")
  public ResponseEntity<MessageResponse<AuthUserResponse>> getMe(Authentication authentication) {
    return messageHttpResponse.success(authService.getCurrentUser(authentication));
  }
}
