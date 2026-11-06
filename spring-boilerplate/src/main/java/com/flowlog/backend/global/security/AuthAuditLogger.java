package com.flowlog.backend.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 인증 감사 로그. (logger 이름 {@code AUDIT} → local 외 프로필은 별도 파일 *-audit.log)
 *
 * <p>형식: {@code [AUDIT] event=... user=... type=... ip=... uri=... reason=...}
 * (traceId 는 로그 패턴의 MDC 로 함께 기록). 토큰·비밀번호 등 비밀 값은 기록하지 않는다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
public class AuthAuditLogger {

  /** 감사 로그 logger 이름. */
  public static final String LOGGER_NAME = "AUDIT";

  private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);

  /** 감사 이벤트. */
  public enum Event {
    /** 로그인 성공. */
    LOGIN_SUCCESS,
    /** 로그인 실패. */
    LOGIN_FAILURE,
    /** 로그아웃. */
    LOGOUT,
    /** 세션 강제 종료 (토큰 만료/갱신 실패, SAML 유효시간 경과). */
    SESSION_REVOKED,
    /** 인증 필요 (401). */
    UNAUTHORIZED,
    /** 권한 없음 (403). */
    FORBIDDEN
  }

  /**
   * 감사 로그를 기록한다.
   *
   * @param event 이벤트
   * @param user 사용자 (없으면 -)
   * @param type 인증 방식 (OAUTH2_SESSION, SAML2, OAUTH2_BEARER 등)
   * @param request 요청
   * @param reason 사유 (선택)
   */
  public void log(
      Event event, String user, String type, HttpServletRequest request, String reason) {
    String line = "[AUDIT] event={} user={} type={} ip={} method={} uri={} reason={}";
    Object[] args = {event, StringUtils.defaultIfBlank(user, "-"),
        StringUtils.defaultIfBlank(type, "-"), request.getRemoteAddr(), request.getMethod(),
        sanitize(request.getRequestURI()), sanitize(StringUtils.defaultIfBlank(reason, "-"))};
    if (event == Event.LOGIN_SUCCESS || event == Event.LOGOUT) {
      AUDIT.info(line, args);
    } else {
      AUDIT.warn(line, args);
    }
  }

  /** 로그 인젝션 방지 (개행 제거, 길이 제한). */
  private static String sanitize(String value) {
    String cleaned = StringUtils.replaceChars(value, "\r\n\t", "   ");
    return StringUtils.abbreviate(cleaned, 200);
  }
}
