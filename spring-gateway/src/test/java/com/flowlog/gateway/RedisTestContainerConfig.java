package com.flowlog.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 Redis 컨테이너. (Rate Limit Lua 스크립트를 실제 Redis 에서 실행)
 *
 * <p>{@code @ServiceConnection} 이 spring.data.redis.host/port 를 컨테이너로 자동 연결한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestContainerConfig {

  /**
   * Redis 컨테이너 (운영과 같은 버전).
   *
   * @return Redis 컨테이너
   */
  @Bean
  @ServiceConnection(name = "redis")
  public GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:8.10.3-alpine"))
        .withExposedPorts(6379);
  }
}
