package com.flowlog.backend.global.cache;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 로컬 캐시 무효화를 다른 인스턴스에 전파한다. (Redis Pub/Sub)
 *
 * <p>캐시(Caffeine)는 인스턴스마다 따로 있으므로, 한 인스턴스에서 권한·공통코드를 바꾸고 캐시를 비워도
 * 다른 인스턴스는 TTL 동안 이전 값을 쓴다. 무효화 시 채널 {@value #CHANNEL} 에 캐시 이름을 발행하고,
 * 다른 인스턴스는 수신 즉시 같은 캐시를 비운다.
 *
 * <ul>
 *   <li>메시지 : {@code {instanceId}|{cacheName}} - 자기가 보낸 메시지는 무시</li>
 *   <li>Redis 장애 시 발행 실패는 경고만 남긴다 (다른 인스턴스는 TTL 뒤 반영)</li>
 *   <li>수신 측은 해당 캐시 전체를 비운다 (키 단위 무효화도 전체 삭제로 처리 - 단순·안전)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Slf4j
public class CacheInvalidationBus implements MessageListener {

  /** 무효화 채널. */
  public static final String CHANNEL = "flowlog:cache:invalidate";

  private static final char SEPARATOR = '|';

  private final String instanceId = UUID.randomUUID().toString();
  private final StringRedisTemplate redisTemplate;
  private CacheManager localCacheManager;

  /**
   * 생성자.
   *
   * @param redisTemplate Redis (null 이면 전파하지 않음 - 단일 인스턴스/테스트)
   */
  public CacheInvalidationBus(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 수신 시 비울 로컬 캐시 관리자. (전파 기능이 없는 원본 관리자 - 수신 처리가 다시 발행하지 않도록)
   *
   * @param localCacheManager 로컬 캐시 관리자
   */
  void bind(CacheManager localCacheManager) {
    this.localCacheManager = localCacheManager;
  }

  /**
   * 전파 사용 여부.
   *
   * @return Redis 가 설정되어 있으면 true
   */
  public boolean isEnabled() {
    return redisTemplate != null;
  }

  /**
   * 캐시 무효화 발행.
   *
   * @param cacheName 캐시 이름
   */
  public void publish(String cacheName) {
    if (redisTemplate == null) {
      return;
    }
    try {
      redisTemplate.convertAndSend(CHANNEL, instanceId + SEPARATOR + cacheName);
    } catch (RuntimeException e) {
      log.warn("[Cache] 무효화 전파 실패 cache={} ({}) - 다른 인스턴스는 TTL 뒤 반영", cacheName,
          e.getClass().getSimpleName());
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    int separator = body.indexOf(SEPARATOR);
    if (separator <= 0 || localCacheManager == null) {
      return;
    }
    if (instanceId.equals(body.substring(0, separator))) {
      return;
    }
    String cacheName = body.substring(separator + 1);
    Cache cache = localCacheManager.getCache(cacheName);
    if (cache != null) {
      cache.clear();
      log.info("[Cache] 다른 인스턴스의 무효화 수신 → {} 비움", cacheName);
    }
  }
}
