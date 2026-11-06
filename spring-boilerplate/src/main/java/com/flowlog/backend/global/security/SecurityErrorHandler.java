package com.flowlog.backend.global.security;

import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.global.exception.CommonErrorCode;
import com.flowlog.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증/인가 실패를 표준 응답 형식으로 반환한다.
 *
 * <ul>
 *   <li>미인증 / 토큰 오류 → 401, C0009</li>
 *   <li>권한 없음 → 403, C0010</li>
 * </ul>
 *
 * <p>Security Filter 단계에서 발생하므로 {@code @RestControllerAdvice} 대신 이 핸들러가 처리한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

  private final MessageHttpResponse messageHttpResponse;
  private final JsonMapper jsonMapper;
  private final AuthAuditLogger auditLogger;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    log.warn("[Unauthorized] {} {} - {}", request.getMethod(), request.getRequestURI(),
        authException.getMessage());
    auditLogger.log(AuthAuditLogger.Event.UNAUTHORIZED, currentUser(), null, request,
        authException.getClass().getSimpleName());
    response.setHeader("WWW-Authenticate", "Bearer");
    write(response, CommonErrorCode.UNAUTHORIZED);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    log.warn("[Forbidden] {} {} - {}", request.getMethod(), request.getRequestURI(),
        accessDeniedException.getMessage());
    auditLogger.log(AuthAuditLogger.Event.FORBIDDEN, currentUser(), null, request, null);
    write(response, CommonErrorCode.FORBIDDEN);
  }

  /**
   * 브라우저 로그인(OAuth2 BFF / SAML2) 실패 : 로그인 페이지로 redirect 하지 않고 401 표준 응답.
   * (실패 사유 상세는 감사 로그에만 남기고 응답에는 노출하지 않는다)
   */
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException {
    auditLogger.log(AuthAuditLogger.Event.LOGIN_FAILURE, null, null, request,
        exception.getClass().getSimpleName() + ": " + exception.getMessage());
    write(response, CommonErrorCode.UNAUTHORIZED);
  }

  private static String currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : authentication.getName();
  }

  private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    ResponseEntity<MessageResponse<Void>> entity = messageHttpResponse.error(errorCode, null);
    response.setStatus(entity.getStatusCode().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    jsonMapper.writeValue(response.getWriter(), entity.getBody());
  }
}
