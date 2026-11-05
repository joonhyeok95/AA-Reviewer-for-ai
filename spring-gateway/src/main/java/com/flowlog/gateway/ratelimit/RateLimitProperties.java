package com.flowlog.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate Limit 설정. ({@code app.gateway.rate-limit.*})
 *
 * <p>토큰 버킷: 초당 {@code replenishRate} 개씩 채워지고 최대 {@code burstCapacity} 개까지 쌓인다.
 * 요청 1건당 {@code requestedTokens} 개를 소비한다.
 *
 * @param replenishRate 초당 충전 토큰 수 (평균 허용 TPS)
 * @param burstCapacity 버킷 최대 크기 (순간 최대 허용 요청 수)
 * @param requestedTokens 요청당 소비 토큰 수
 * @param sessionKeyPrefix Spring Session 의 Redis 세션 키 접두사. SESSION 쿠키의 세션이
 *     이 키로 Redis 에 실제로 존재할 때만 세션 단위 버킷을 쓴다. (위조 쿠키 → IP 버킷)
 * @param trustedProxyHops Gateway 앞단 신뢰 프록시(LB) 단계 수. 0 이면 TCP 접속 IP,
 *     1 이상이면 X-Forwarded-For 의 오른쪽에서 n 번째 값을 클라이언트 IP 로 사용한다.
 *     (LB 뒤에서 0 이면 모든 사용자가 LB IP 하나의 버킷을 공유하게 된다)
 * @author flowlog
 * @since 2026-11-05
 */
@ConfigurationProperties(prefix = "app.gateway.rate-limit")
public record RateLimitProperties(
    int replenishRate,
    int burstCapacity,
    int requestedTokens,
    @DefaultValue("flowlog:session:sessions:") String sessionKeyPrefix,
    @DefaultValue("0") int trustedProxyHops) {
}
