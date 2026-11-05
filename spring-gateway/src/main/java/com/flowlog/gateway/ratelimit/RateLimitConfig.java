package com.flowlog.gateway.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Rate Limit 구성.
 *
 * <ul>
 *   <li>{@link RedisRateLimiter} : Redis 에서 Lua 스크립트({@code request_rate_limiter.lua})를
 *       EVALSHA 로 실행하는 토큰 버킷. "토큰 조회 → 허용 판단 → 차감 → 저장" 이 한 스크립트로
 *       원자적으로 실행되므로 Gateway 가 여러 대여도 경합 없이 정확하게 제한된다.</li>
 *   <li>키 : Redis 에 실제로 존재하는 로그인 세션 단위, 그 외는 클라이언트 IP 단위
 *       ({@link SessionOrIpKeyResolver})</li>
 * </ul>
 *
 * <p>Redis 장애 시 RedisRateLimiter 는 요청을 허용(fail-open)하므로 서비스는 계속된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

  /**
   * Redis 토큰 버킷 Rate Limiter.
   *
   * @param properties 설정
   * @return RedisRateLimiter
   */
  @Bean
  public RedisRateLimiter redisRateLimiter(RateLimitProperties properties) {
    return new RedisRateLimiter(
        properties.replenishRate(), properties.burstCapacity(), properties.requestedTokens());
  }

  /**
   * Rate Limit 키 결정 (Redis 에 존재하는 세션 또는 클라이언트 IP).
   *
   * @param redisTemplate Redis
   * @param properties 설정
   * @return KeyResolver
   */
  @Bean
  public KeyResolver sessionOrIpKeyResolver(
      ReactiveStringRedisTemplate redisTemplate, RateLimitProperties properties) {
    return new SessionOrIpKeyResolver(
        redisTemplate, properties.sessionKeyPrefix(), properties.trustedProxyHops());
  }
}
