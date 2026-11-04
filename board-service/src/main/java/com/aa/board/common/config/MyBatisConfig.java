package com.aa.board.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 복잡한 조회 쿼리는 MyBatis 매퍼(@Mapper 인터페이스 + XML)로 작성한다.
 */
@Configuration
@MapperScan(basePackages = "com.aa.board.**.mapper")
public class MyBatisConfig {
}
