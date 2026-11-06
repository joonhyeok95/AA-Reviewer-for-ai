package com.flowlog.backend.global.config;

import com.flowlog.backend.global.security.AppSecurityProperties;
import com.flowlog.backend.global.web.TrustedForwardedHeaderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Gateway 뒤 동작 설정 : 신뢰 프록시가 보낸 X-Forwarded-* 만 반영한다.
 *
 * <p>{@code server.forward-headers-strategy=none} 으로 Boot 기본 필터를 끄고 이 필터를 사용한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
public class ForwardedHeaderConfig {

  /**
   * 신뢰 프록시 Forwarded 헤더 필터 (가장 먼저 실행).
   *
   * @param properties 보안 설정 (trusted-proxies)
   * @return 필터 등록
   */
  @Bean
  public FilterRegistrationBean<TrustedForwardedHeaderFilter> trustedForwardedHeaderFilter(
      AppSecurityProperties properties) {
    FilterRegistrationBean<TrustedForwardedHeaderFilter> registration =
        new FilterRegistrationBean<>(new TrustedForwardedHeaderFilter(properties.trustedProxies()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
