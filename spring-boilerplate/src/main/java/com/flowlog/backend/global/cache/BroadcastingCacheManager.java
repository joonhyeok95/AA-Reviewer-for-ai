package com.flowlog.backend.global.cache;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 무효화(evict / clear)를 다른 인스턴스에 전파하는 캐시 관리자. (로컬 캐시 관리자 데코레이터)
 *
 * <p>트랜잭션 안에서 무효화하면 <b>커밋 후</b> 로컬 캐시를 한 번 더 비우고 전파한다.
 * (커밋 전에 비우면 그 사이 다른 요청이 이전 데이터를 다시 캐시할 수 있으므로)
 *
 * @author flowlog
 * @since 2026-11-06
 */
public class BroadcastingCacheManager implements CacheManager {

  private final CacheManager delegate;
  private final CacheInvalidationBus bus;
  private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

  /**
   * 생성자.
   *
   * @param delegate 로컬 캐시 관리자 (Caffeine)
   * @param bus 무효화 전파
   */
  public BroadcastingCacheManager(CacheManager delegate, CacheInvalidationBus bus) {
    this.delegate = delegate;
    this.bus = bus;
    bus.bind(delegate);
  }

  @Override
  public Cache getCache(String name) {
    Cache cache = caches.get(name);
    if (cache == null) {
      Cache target = delegate.getCache(name);
      if (target != null) {
        cache = caches.computeIfAbsent(name, key -> new BroadcastingCache(target, bus));
      }
    }
    return cache;
  }

  @Override
  public Collection<String> getCacheNames() {
    return delegate.getCacheNames();
  }

  /** 무효화 시 전파하는 캐시. */
  static final class BroadcastingCache implements Cache {

    private final Cache target;
    private final CacheInvalidationBus bus;

    BroadcastingCache(Cache target, CacheInvalidationBus bus) {
      this.target = target;
      this.bus = bus;
    }

    @Override
    public String getName() {
      return target.getName();
    }

    @Override
    public Object getNativeCache() {
      return target.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
      return target.get(key);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
      return target.get(key, type);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
      return target.get(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
      target.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
      return target.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
      target.evict(key);
      propagate();
    }

    @Override
    public boolean evictIfPresent(Object key) {
      boolean present = target.evictIfPresent(key);
      propagate();
      return present;
    }

    @Override
    public void clear() {
      target.clear();
      propagate();
    }

    @Override
    public boolean invalidate() {
      boolean invalidated = target.invalidate();
      propagate();
      return invalidated;
    }

    /** 커밋 후(트랜잭션이 없으면 즉시) 로컬 캐시를 다시 비우고 다른 인스턴스에 전파. */
    private void propagate() {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            target.clear();
            bus.publish(getName());
          }
        });
      } else {
        bus.publish(getName());
      }
    }
  }
}
