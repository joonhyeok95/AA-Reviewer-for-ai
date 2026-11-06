package com.flowlog.backend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 후 복귀 경로 저장 필터.
 *
 * <p>화면(SPA)은 {@code /oauth2/authorization/{id}?returnTo=/support/notices} 처럼 로그인을 시작한다.
 * 이 필터가 {@code returnTo} 를 검증해 세션에 저장하고, 로그인 성공 핸들러가 그 경로로 redirect 한다.
 *
 * <p>Open Redirect 방지: 같은 호스트의 절대 경로만 허용한다. ({@code /} 로 시작, {@code //}·역슬래시 금지,
 * 허용 문자만, 500자 이하) 그 외 값은 무시하고 기본 성공 URL 로 이동한다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Slf4j
public class LoginReturnUrlFilter extends OncePerRequestFilter {

  /** 세션 속성 이름 (String). */
  public static final String RETURN_URL_ATTRIBUTE =
      LoginReturnUrlFilter.class.getName() + ".RETURN_URL";

  /** 요청 파라미터 이름. */
  public static final String PARAMETER = "returnTo";

  private static final List<String> LOGIN_START_PATHS =
      List.of("/oauth2/authorization/", "/saml2/authenticate/");

  private static final Pattern SAFE_RETURN_URL =
      Pattern.compile("^/(?![/\\\\])[A-Za-z0-9\\-._~/?=&%]{0,499}$");

  /**
   * 복귀 경로가 안전한지 검사한다.
   *
   * @param url 복귀 경로
   * @return 같은 호스트의 절대 경로이면 true
   */
  public static boolean isSafe(String url) {
    return url != null && SAFE_RETURN_URL.matcher(url).matches();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = StringUtils.removeStart(request.getRequestURI(), request.getContextPath());
    return LOGIN_START_PATHS.stream().noneMatch(prefix -> StringUtils.startsWith(path, prefix));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String returnTo = request.getParameter(PARAMETER);
    if (isSafe(returnTo)) {
      request.getSession().setAttribute(RETURN_URL_ATTRIBUTE, returnTo);
    } else {
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.removeAttribute(RETURN_URL_ATTRIBUTE);
      }
      if (returnTo != null) {
        log.warn("[Login] 허용하지 않는 복귀 경로 무시 length={}", returnTo.length());
      }
    }
    chain.doFilter(request, response);
  }
}
