package com.flowlog.backend.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시계 (현재 시각). 테스트에서 고정 시각으로 대체할 수 있도록 Bean 으로 주입한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
public class ClockConfig {

  /**
   * 서비스 시간대 시계.
   *
   * @return Clock (Asia/Seoul)
   */
  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
