package com.flowlog.backend.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF 토큰 쿠키(XSRF-TOKEN) 발급 필터. (SPA)
 *
 * <p>CSRF 토큰은 필요할 때 만들어지므로(deferred), 화면이 첫 변경 요청 전에 쿠키를 갖고 있도록 매 요청에서 토큰을
 * 읽어 둔다. 쿠키가 이미 있으면 그대로 쓰고, 없을 때만 새로 발급(Set-Cookie)된다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
      csrfToken.getToken();
    }
    chain.doFilter(request, response);
  }
}
