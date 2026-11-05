package com.flowlog.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowlog.backend.menu.domain.service.MenuService;
import com.flowlog.backend.notice.domain.repository.NoticeI18nRepository;
import com.flowlog.backend.role.domain.service.UserAuthorityService;
import com.flowlog.backend.role.domain.service.dto.UserAuthority;
import com.flowlog.backend.sample.domain.repository.SampleRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * 실제 Oracle(Free 23ai) 에서 DDL·초기 데이터 실행 + Entity 매핑 검증(ddl-auto=validate).
 *
 * <p>H2 Oracle 모드와 실제 Oracle 의 차이(타입, 시퀀스 문법, 예약어)를 잡기 위한 테스트.
 * 이미지가 크므로 기본 빌드에서는 제외되고 {@code ./mvnw test -Poracle-it} 로 실행한다. (Docker 필요)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Tag("oracle")
@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(properties = {
    // dev 프로필의 환경변수 자리값 (실제 접속 정보는 @ServiceConnection 이 덮어씀)
    "DB_URL=jdbc:oracle:thin:@//placeholder:1521/FREEPDB1",
    "DB_USERNAME=placeholder",
    "DB_PASSWORD=placeholder",
    "KEYCLOAK_BFF_CLIENT_SECRET=test-secret",
    "SESSION_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    // 운영과 같은 DDL/데이터 스크립트 실행 후 Hibernate 가 Entity 와 스키마를 검증
    "spring.sql.init.mode=always",
    "spring.sql.init.encoding=UTF-8",
    "spring.sql.init.schema-locations=classpath:db/ddl/00_sample.sql,"
        + "classpath:db/ddl/01_auth_menu_code.sql,classpath:db/ddl/02_notice.sql",
    "spring.sql.init.data-locations=classpath:db/data/00_sample.sql,"
        + "classpath:db/data/01_auth_menu_code.sql,classpath:db/data/02_notice.sql",
    "spring.jpa.defer-datasource-initialization=false",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class OracleSchemaTest {

  @Container
  @ServiceConnection
  static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

  @Autowired
  private UserAuthorityService userAuthorityService;

  @Autowired
  private MenuService menuService;

  @Autowired
  private SampleRepository sampleRepository;

  @Autowired
  private NoticeI18nRepository noticeI18nRepository;

  @Test
  @DisplayName("Oracle - DDL/초기 데이터 실행, Entity 검증(validate) 통과, 권한·메뉴 조회 쿼리 동작")
  void schemaAndQueries() {
    UserAuthority viewer =
        userAuthorityService.loadBySubject("00000000-0000-4000-8000-000000000004");

    assertThat(viewer.userId()).isEqualTo("tx.viewer");
    assertThat(viewer.roleCds()).contains("TX_MANAGER_READ", "USER_EMP");
    assertThat(viewer.permissions()).containsExactly("notice.list", "sample.list");
    assertThat(menuService.getMyMenus("00000000-0000-4000-8000-000000000001", "ko"))
        .hasSize(5);
    assertThat(sampleRepository.count()).isPositive();
  }

  @Test
  @DisplayName("Oracle - 공지 검색 (사용자 언어 버전 → 대표 언어 대체, CLOB 본문 LIKE 검색)")
  void noticeSearch() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

    assertThat(noticeI18nRepository.searchDisplayed("en", null, null, "N", now,
            PageRequest.of(0, 10)).getContent())
        .extracting(i -> i.getId().getNoticeId() + ":" + i.getLangCd())
        .containsExactly("3:en", "2:ko", "1:en");
    assertThat(noticeI18nRepository.searchDisplayed("en", null, "%정기 점검%", "N", now,
            PageRequest.of(0, 10)).getContent())
        .extracting(i -> i.getId().getNoticeId()).containsExactly(2L);
    assertThat(noticeI18nRepository.searchDisplayed("ko", null, "%town hall%", "Y", now,
            PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
  }
}
