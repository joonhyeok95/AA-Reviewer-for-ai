package com.flowlog.backend.global.web;

import com.flowlog.backend.global.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 단위 추적 ID(traceId) 필터.
 *
 * <p>요청 Header {@code X-Trace-Id} 가 있으면 재사용하고 없으면 생성한다.
 * MDC 에 저장해 모든 로그에 traceId 가 찍히도록 하고, 응답 Header 로도 반환한다.
 * (Gateway 등 상위 시스템과 로그 연계용)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** 외부 입력 traceId 허용 형식 (로그 인젝션 방지). */
  private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = resolveTraceId(request.getHeader(HeaderConstants.TRACE_ID_HEADER));
    MDC.put(HeaderConstants.TRACE_ID_MDC_KEY, traceId);
    response.setHeader(HeaderConstants.TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(HeaderConstants.TRACE_ID_MDC_KEY);
    }
  }

  private String resolveTraceId(String headerValue) {
    String traceId = UUID.randomUUID().toString().replace("-", "");
    if (headerValue != null && VALID_TRACE_ID.matcher(headerValue).matches()) {
      traceId = headerValue;
    }
    return traceId;
  }
}
