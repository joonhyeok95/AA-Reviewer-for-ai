package com.flowlog.backend.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 신뢰할 수 있는 프록시(Gateway)가 보낸 경우에만 Forwarded / X-Forwarded-* 헤더를 반영한다.
 *
 * <p>Spring 의 {@link ForwardedHeaderFilter}({@code server.forward-headers-strategy=framework})는
 * 호출자를 구분하지 않고 헤더를 신뢰한다. 백엔드 포트에 직접 접근한 클라이언트가
 * {@code X-Forwarded-Host: evil.example} 을 보내면 redirect / SAML ACS / Swagger URL 이 조작된다.
 * 이 필터는 직전 호출자 IP 가 {@code app.security.trusted-proxies} 와 일치할 때만
 * {@link ForwardedHeaderFilter} 를 적용하고, 그 외에는 해당 헤더를 제거한 채 처리한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
public class TrustedForwardedHeaderFilter extends OncePerRequestFilter {

  private static final List<String> FORWARDED_HEADERS = List.of(
      "forwarded", "x-forwarded-host", "x-forwarded-port", "x-forwarded-proto",
      "x-forwarded-prefix", "x-forwarded-ssl", "x-forwarded-for");

  private final Pattern trustedProxies;
  private final ForwardedHeaderFilter forwardedHeaderFilter = new ForwardedHeaderFilter();

  /**
   * 생성자.
   *
   * @param trustedProxies 신뢰 프록시 IP 정규식
   */
  public TrustedForwardedHeaderFilter(String trustedProxies) {
    this.trustedProxies = Pattern.compile(trustedProxies);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (trustedProxies.matcher(request.getRemoteAddr()).matches()) {
      forwardedHeaderFilter.doFilter(request, response, chain);
    } else {
      if (hasForwardedHeader(request)) {
        log.warn("[ForwardedHeader] 신뢰하지 않는 호출자의 Forwarded 헤더 무시 remote={}",
            request.getRemoteAddr());
      }
      chain.doFilter(new ForwardedHeaderRemovingRequest(request), response);
    }
  }

  private static boolean hasForwardedHeader(HttpServletRequest request) {
    return FORWARDED_HEADERS.stream().anyMatch(name -> request.getHeader(name) != null);
  }

  /** Forwarded / X-Forwarded-* 헤더를 숨기는 요청. */
  private static final class ForwardedHeaderRemovingRequest extends HttpServletRequestWrapper {

    ForwardedHeaderRemovingRequest(HttpServletRequest request) {
      super(request);
    }

    private static boolean isForwarded(String name) {
      return name != null && FORWARDED_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String getHeader(String name) {
      return isForwarded(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isForwarded(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
          .filter(name -> !isForwarded(name)).toList());
    }
  }
}
