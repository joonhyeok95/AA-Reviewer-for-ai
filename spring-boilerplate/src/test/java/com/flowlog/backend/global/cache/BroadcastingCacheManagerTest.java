package com.flowlog.backend.global.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 캐시 무효화 전파 (인스턴스 간 권한·공통코드 캐시 동기화).
 *
 * @author flowlog
 * @since 2026-11-06
 */
class BroadcastingCacheManagerTest {

  private static final String CACHE = "userAuthority";

  private StringRedisTemplate redis;
  private ConcurrentMapCacheManager local;
  private CacheInvalidationBus bus;
  private BroadcastingCacheManager manager;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    local = new ConcurrentMapCacheManager(CACHE);
    bus = new CacheInvalidationBus(redis);
    manager = new BroadcastingCacheManager(local, bus);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("트랜잭션 밖 clear - 로컬 캐시를 비우고 즉시 전파")
  void clearWithoutTransaction() {
    // given
    Cache cache = manager.getCache(CACHE);
    cache.put("sub-1", "old");

    // when
    cache.clear();

    // then
    assertThat(local.getCache(CACHE).get("sub-1")).isNull();
    then(redis).should().convertAndSend(eq(CacheInvalidationBus.CHANNEL), startsWith(""));
  }

  @Test
  @DisplayName("트랜잭션 안 clear - 커밋 후에 로컬 캐시를 다시 비우고 전파 (커밋 전 재캐시 방지)")
  void clearAfterCommit() {
    // given
    TransactionSynchronizationManager.initSynchronization();
    Cache cache = manager.getCache(CACHE);

    // when : 무효화 후 커밋 전에 다른 요청이 이전 값을 다시 캐시
    cache.clear();
    cache.put("sub-1", "stale");
    then(redis).should(never()).convertAndSend(anyString(), anyString());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    // then
    assertThat(local.getCache(CACHE).get("sub-1")).isNull();
    then(redis).should().convertAndSend(eq(CacheInvalidationBus.CHANNEL), anyString());
  }

  @Test
  @DisplayName("다른 인스턴스의 메시지는 로컬 캐시를 비우고, 자기 메시지는 무시")
  void onMessage() {
    // given
    local.getCache(CACHE).put("sub-1", "value");
    String[] published = new String[1];
    given(redis.convertAndSend(eq(CacheInvalidationBus.CHANNEL), anyString()))
        .willAnswer(invocation -> {
          published[0] = invocation.getArgument(1);
          return 1L;
        });
    bus.publish(CACHE);

    // when : 자기 메시지
    bus.onMessage(message(published[0]), null);

    // then
    assertThat(local.getCache(CACHE).get("sub-1")).isNotNull();

    // when : 다른 인스턴스 메시지
    bus.onMessage(message("other-instance|" + CACHE), null);

    // then
    assertThat(local.getCache(CACHE).get("sub-1")).isNull();
  }

  @Test
  @DisplayName("Redis 미설정(단일 인스턴스) - 로컬만 비우고 전파하지 않음, Redis 오류는 무시")
  void disabledOrRedisDown() {
    // given
    BroadcastingCacheManager single =
        new BroadcastingCacheManager(new ConcurrentMapCacheManager(CACHE),
            new CacheInvalidationBus(null));
    given(redis.convertAndSend(anyString(), anyString()))
        .willThrow(new IllegalStateException("redis down"));

    // when / then : 예외 없이 처리
    single.getCache(CACHE).clear();
    manager.getCache(CACHE).clear();
    assertThat(new CacheInvalidationBus(null).isEnabled()).isFalse();
  }

  private static DefaultMessage message(String body) {
    return new DefaultMessage(CacheInvalidationBus.CHANNEL.getBytes(StandardCharsets.UTF_8),
        body.getBytes(StandardCharsets.UTF_8));
  }
}
