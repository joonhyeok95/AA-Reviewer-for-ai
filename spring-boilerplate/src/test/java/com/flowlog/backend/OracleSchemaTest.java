package com.flowlog.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowlog.backend.code.domain.service.CodeAdminService;
import com.flowlog.backend.global.util.LikePatterns;
import com.flowlog.backend.menu.domain.service.MenuAdminService;
import com.flowlog.backend.menu.domain.service.MenuService;
import com.flowlog.backend.notice.domain.repository.NoticeSearchMapper;
import com.flowlog.backend.notice.domain.service.dto.NoticeSearchRow;
import com.flowlog.backend.role.domain.service.RoleAdminService;
import com.flowlog.backend.role.domain.service.UserAuthorityService;
import com.flowlog.backend.role.domain.service.dto.UserAuthority;
import com.flowlog.backend.sample.domain.repository.SampleRepository;
import com.flowlog.backend.user.domain.service.UserQueryService;
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
        + "classpath:db/ddl/01_auth_menu_code.sql,classpath:db/ddl/02_notice.sql,"
        + "classpath:db/ddl/03_file_store.sql,classpath:db/ddl/04_calendar.sql,"
        + "classpath:db/ddl/05_church.sql",
    "spring.sql.init.data-locations=classpath:db/data/00_sample.sql,"
        + "classpath:db/data/01_auth_menu_code.sql,classpath:db/data/02_notice.sql,"
        + "classpath:db/data/03_calendar.sql,classpath:db/data/04_church.sql",
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
  private NoticeSearchMapper noticeSearchMapper;

  @Test
  @DisplayName("Oracle - DDL/초기 데이터 실행, Entity 검증(validate) 통과, 권한·메뉴 조회 쿼리 동작")
  void schemaAndQueries() {
    UserAuthority viewer =
        userAuthorityService.loadBySubject("00000000-0000-4000-8000-000000000004");

    assertThat(viewer.userId()).isEqualTo("tx.viewer");
    assertThat(viewer.roleCds()).contains("TX_MANAGER_READ", "USER_EMP");
    assertThat(viewer.permissions()).containsExactly("board.list", "bulletin.list", "calendar.list",
        "catechism.list", "liturgy.list", "sample.list");
    assertThat(menuService.getMyMenus("00000000-0000-4000-8000-000000000001", "ko"))
        .hasSize(7);
    assertThat(sampleRepository.count()).isPositive();
    // LIKE ... ESCAPE '\' (사용자 입력 와일드카드 이스케이프)
    assertThat(sampleRepository.search(LikePatterns.contains("샘플"), null, PageRequest.of(0, 10))
        .getTotalElements()).isEqualTo(3);
    assertThat(sampleRepository.search(LikePatterns.contains("%"), null, PageRequest.of(0, 10))
        .getTotalElements()).isZero();
  }

  @Autowired
  private CodeAdminService codeAdminService;

  @Autowired
  private RoleAdminService roleAdminService;

  @Autowired
  private MenuAdminService menuAdminService;

  @Autowired
  private UserQueryService userQueryService;

  @Test
  @DisplayName("Oracle - 관리 화면 조회 (그룹별 건수, 권한별 부여·메뉴 수, 메뉴 트리, 사용자 LIKE ESCAPE 검색)")
  void adminQueries() {
    assertThat(codeAdminService.getGroups("ko"))
        .anySatisfy(group -> {
          assertThat(group.groupCd()).isEqualTo("LANG");
          assertThat(group.codeCnt()).isEqualTo(2);
        });
    assertThat(roleAdminService.getRoles("en"))
        .anySatisfy(role -> {
          assertThat(role.roleCd()).isEqualTo("USER_EMP");
          assertThat(role.deptGrantCnt()).isEqualTo(4);
          assertThat(role.menuCnt()).isPositive();
        });
    assertThat(roleAdminService.getGrants(2L, "ko").users())
        .extracting(grant -> grant.targetId()).contains("tx.master");
    assertThat(menuAdminService.getTree("ko")).isNotEmpty();
    assertThat(userQueryService.search("EMP", "ko"))
        .extracting(user -> user.userId()).contains("emp01");
    assertThat(userQueryService.search("%", "ko")).isEmpty();
  }

  @Test
  @DisplayName("Oracle - 공지 검색 MyBatis (언어 대체, CLOB 본문 LIKE, NULL 바인딩, OFFSET/FETCH 페이징)")
  void noticeSearch() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

    assertThat(noticeSearchMapper.searchDisplayed("en", null, null, false, now, 0, 10))
        .extracting(row -> row.noticeId() + ":" + row.langCd())
        .containsExactly("3:en", "2:ko", "1:en");
    assertThat(noticeSearchMapper.searchDisplayed("en", null, null, false, now, 1, 1))
        .extracting(NoticeSearchRow::noticeId).containsExactly(2L);
    assertThat(noticeSearchMapper.searchDisplayed("en", null, "%정기 점검%", false, now, 0, 10))
        .extracting(NoticeSearchRow::noticeId).containsExactly(2L);
    assertThat(noticeSearchMapper.countDisplayed("ko", null, "%town hall%", true, now))
        .isEqualTo(1);
    assertThat(noticeSearchMapper.countDisplayed("ko", "SYSTEM", null, false, now)).isEqualTo(1);
  }
}
