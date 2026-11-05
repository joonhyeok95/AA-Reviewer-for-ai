package com.flowlog.backend.global.config;

import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.global.security.AppSecurityProperties;
import com.flowlog.backend.role.domain.service.UserAuthorityService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 캐시 (Caffeine).
 *
 * <ul>
 *   <li>{@code userAuthority} : 사용자 DB 권한. 요청마다 조회되므로 짧게 캐시한다.
 *       ({@code app.security.authority-cache-ttl}, 권한 변경은 최대 이 시간 뒤 반영)</li>
 *   <li>{@code codeItems} : 공통코드(그룹 단위, LANG 포함). 같은 TTL</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
@EnableCaching
public class CacheConfig {

  /**
   * 캐시 관리자.
   *
   * @param properties 보안 설정
   * @return CacheManager
   */
  @Bean
  public CacheManager cacheManager(AppSecurityProperties properties) {
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(UserAuthorityService.CACHE_NAME, CodeService.CACHE_NAME);
    cacheManager.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(properties.authorityCacheTtl())
        .maximumSize(10_000)
        .recordStats());
    return cacheManager;
  }
}
