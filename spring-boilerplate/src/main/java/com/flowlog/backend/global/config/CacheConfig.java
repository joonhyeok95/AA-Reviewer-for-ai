package com.flowlog.backend.global.config;

import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.global.cache.BroadcastingCacheManager;
import com.flowlog.backend.global.cache.CacheInvalidationBus;
import com.flowlog.backend.global.security.AppSecurityProperties;
import com.flowlog.backend.role.domain.service.UserAuthorityService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 로컬 캐시 (Caffeine) + 인스턴스 간 무효화 전파 (Redis Pub/Sub).
 *
 * <ul>
 *   <li>{@code userAuthority} : 사용자 DB 권한. 요청마다 조회되므로 짧게 캐시한다.
 *       ({@code app.security.authority-cache-ttl})</li>
 *   <li>{@code codeItems} : 공통코드(그룹 단위, LANG 포함). 같은 TTL</li>
 *   <li>관리 화면에서 권한·메뉴·공통코드를 바꾸면 ({@code @CacheEvict}) 커밋 후 모든 인스턴스의 캐시를
 *       비운다 ({@link CacheInvalidationBus}). Redis 장애 시에는 TTL 뒤 반영.</li>
 *   <li>{@code app.cache.broadcast-enabled=false} 면 전파하지 않는다 (단일 인스턴스, 테스트)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
@EnableCaching
public class CacheConfig {

  private static final String BROADCAST_ENABLED = "app.cache.broadcast-enabled";

  /**
   * 캐시 무효화 전파.
   *
   * @param enabled 전파 사용 여부
   * @param redisTemplate Redis
   * @return CacheInvalidationBus
   */
  @Bean
  public CacheInvalidationBus cacheInvalidationBus(
      @Value("${" + BROADCAST_ENABLED + ":true}") boolean enabled,
      ObjectProvider<StringRedisTemplate> redisTemplate) {
    return new CacheInvalidationBus(enabled ? redisTemplate.getIfAvailable() : null);
  }

  /**
   * 캐시 관리자. (Caffeine + 무효화 전파)
   *
   * @param properties 보안 설정
   * @param bus 무효화 전파
   * @return CacheManager
   */
  @Bean
  public CacheManager cacheManager(AppSecurityProperties properties, CacheInvalidationBus bus) {
    CaffeineCacheManager local =
        new CaffeineCacheManager(UserAuthorityService.CACHE_NAME, CodeService.CACHE_NAME);
    local.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(properties.authorityCacheTtl())
        .maximumSize(10_000)
        .recordStats());
    return new BroadcastingCacheManager(local, bus);
  }

  /**
   * 다른 인스턴스의 무효화 수신.
   *
   * @param connectionFactory Redis 연결
   * @param bus 무효화 전파
   * @return 리스너 컨테이너
   */
  @Bean
  @ConditionalOnProperty(name = BROADCAST_ENABLED, havingValue = "true", matchIfMissing = true)
  public RedisMessageListenerContainer cacheInvalidationListener(
      RedisConnectionFactory connectionFactory, CacheInvalidationBus bus) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(bus, new ChannelTopic(CacheInvalidationBus.CHANNEL));
    return container;
  }
}
