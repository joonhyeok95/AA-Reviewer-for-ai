package com.flowlog.backend.global.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 스키마·초기 데이터를 <b>DB 가 비어 있을 때만</b> 넣는다. ({@code spring.sql.init.*} 그대로 사용)
 *
 * <ul>
 *   <li>메모리 H2 (local, 테스트) : 기동마다 비어 있으므로 지금처럼 매번 넣는다</li>
 *   <li>영속 DB (localdb - Docker Oracle 볼륨) : 첫 기동에만 넣고, 이후에는 등록·수정한 데이터를 유지</li>
 * </ul>
 *
 * <p>스크립트가 모두 성공하면 완료 표시 테이블({@value #DONE_TABLE})을 만든다.
 * 표시 없이 업무 테이블({@value #SCHEMA_TABLE})만 있으면 지난 초기화가 중간에 실패한 것이므로
 * 반쯤 채워진 DB 로 기동하지 않고 초기화(dev-up -ResetDb)를 안내한다.
 * 운영·dev 는 {@code spring.sql.init.mode=never} 라 이 설정이 동작하지 않는다.
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlInitializationProperties.class)
@ConditionalOnProperty(prefix = "spring.sql.init", name = "mode", havingValue = "always")
public class InitOnceDatabaseConfig {

  /** 초기화 완료 표시 테이블 (업무 데이터 없음). */
  static final String DONE_TABLE = "TB_DB_INIT";

  /** 업무 스키마가 있는지 보는 테이블 (01_auth_menu_code.sql). */
  static final String SCHEMA_TABLE = "TB_MENU";

  /**
   * DB 가 비어 있을 때만 스크립트를 실행하는 초기화기. (Boot 기본 초기화기 대신 - 같은 타입이라 JPA 보다 먼저 실행)
   *
   * @param dataSource 데이터소스
   * @param properties spring.sql.init 설정
   * @return 초기화기
   */
  @Bean
  public ApplicationDataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer(
      DataSource dataSource, SqlInitializationProperties properties) {
    return new ApplicationDataSourceScriptDatabaseInitializer(dataSource, properties) {
      @Override
      public boolean initializeDatabase() {
        if (tableExists(dataSource, DONE_TABLE)) {
          log.info("[DB] 기존 데이터 사용 - 초기 스크립트 건너뜀 (처음부터 다시: dev-up -ResetDb)");
          return false;
        }
        if (tableExists(dataSource, SCHEMA_TABLE)) {
          throw new IllegalStateException("[DB] 지난 초기화가 끝나지 않은 DB 입니다 ("
              + DONE_TABLE + " 없음). 볼륨을 지우고 다시 만드세요: scripts/dev-up -ResetDb "
              + "(또는 docker compose -f infra/oracle/docker-compose.yml down -v)");
        }
        log.info("[DB] 빈 DB - 스키마·초기 데이터 생성");
        boolean applied = super.initializeDatabase();
        markDone(dataSource);
        log.info("[DB] 스키마·초기 데이터 생성 완료");
        return applied;
      }
    };
  }

  /**
   * 테이블이 있는지. (현재 사용자 스키마)
   *
   * @param dataSource 데이터소스
   * @param table 테이블 (대문자)
   * @return 있으면 true
   */
  static boolean tableExists(DataSource dataSource, String table) {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData meta = connection.getMetaData();
      try (ResultSet tables = meta.getTables(null, connection.getSchema(), table,
          new String[] {"TABLE"})) {
        return tables.next();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("DB 초기화 여부를 확인하지 못했습니다", e);
    }
  }

  private static void markDone(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE " + DONE_TABLE + " (INIT_AT TIMESTAMP(6) NOT NULL)");
      statement.execute("INSERT INTO " + DONE_TABLE + " (INIT_AT) VALUES (SYSTIMESTAMP)");
      if (!connection.getAutoCommit()) {
        connection.commit();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("DB 초기화 완료 표시를 남기지 못했습니다", e);
    }
  }
}
