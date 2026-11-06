package com.flowlog.backend.global.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA 설정. (Auditing: 생성/수정 일시, 생성/수정자 자동 기록)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

  /** 인증 정보가 없을 때(배치, 초기화 등) 기본 사용자. */
  private static final String SYSTEM_USER = "system";

  /**
   * 현재 사용자 ID 제공자.
   *
   * <p>로그인 사용자ID (OAuth2: preferred_username, SAML2: NameID, 서비스계정: service-account-*).
   * 인증 정보가 없으면 "system".
   *
   * @return AuditorAware
   */
  @Bean
  public AuditorAware<String> auditorAware() {
    return () -> Optional.of(
        Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName)
            .orElse(SYSTEM_USER));
  }
}
