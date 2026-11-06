package com.flowlog.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 애플리케이션 기동 테스트.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest
class SpringBoilerplateApplicationTests {

  @Test
  @DisplayName("애플리케이션 Context 정상 기동 (DDL 스크립트 스키마 생성 + 초기 데이터 적재 포함)")
  void contextLoads() {
    // Context 기동 자체가 검증 대상
  }
}
