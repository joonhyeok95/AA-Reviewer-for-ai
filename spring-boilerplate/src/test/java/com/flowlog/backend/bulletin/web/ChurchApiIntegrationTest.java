package com.flowlog.backend.bulletin.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 교회 (주보·교리문답·전례문) API 통합 테스트. (H2 + 초기 데이터 db/data/04_church.sql)
 *
 * <p>기준일은 2026-11-10 (화) 로 고정 - 이번 주 주보 = 2026-11-08 샘플.
 *
 * @author flowlog
 * @since 2026-11-08
 */
@SpringBootTest
@Transactional
@Import(ChurchApiIntegrationTest.FixedClock.class)
class ChurchApiIntegrationTest {

  @Autowired
  private WebApplicationContext context;

  private MockMvc admin;

  @TestConfiguration
  static class FixedClock {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
  }

  @BeforeEach
  void setUp() {
    admin = mvc("admin", "ROLE_SYS_ADMIN");
  }

  private MockMvc mvc(String subject, String... authorities) {
    return MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .defaultRequest(get("/").with(jwt().jwt(token -> token.subject(subject))
            .authorities(java.util.Arrays.stream(authorities)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList())))
        .build();
  }

  private static final String BULLETIN_BODY = """
      {"worshipDt": "%s", "volumeNo": 9, "issueNo": %d, "themeVerse": "말씀", "themeVerseRef": "엡 2:15",
       "preacherNm": "홍길동 목사", "statusCd": "%s",
       "orders": [{"itemNm": "찬 양", "detail": "8장"}, {"itemNm": "말씀선포", "detail": "홍길동 목사"}],
       "news": [
         {"title": "기도제목", "children": [
           {"title": "공예배", "content": "모임정착", "children": [{"content": "주일예배"}]}]},
         {"title": "행사 및 일정", "children": [{"title": "금요기도회", "scheduleText": "매주",
           "highlightYn": "Y"}]}],
       "duties": [{"roleNm": "식사", "dutyDt": "2026-11-15", "members": "가람"},
                  {"roleNm": "식사", "dutyDt": "2026-11-22", "members": "나래"},
                  {"roleNm": "안내", "dutyDt": "2026-11-15", "members": "다온"}],
       "catechismUnitIds": [4], "liturgyTextIds": [2, 1]}
      """;

  @Nested
  @DisplayName("주보")
  class BulletinTest {

    @Test
    @DisplayName("이번 주 주보 - 기준일(11/10) 이후 6일 안에 없으면 가장 최근 게시 주보(11/08), 교리문답·전례문 본문 포함")
    void current() throws Exception {
      admin.perform(get("/api/v1/bulletins/current"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.worshipDt").value("2026-11-08"))
          .andExpect(jsonPath("$.result.orders.length()").value(12))
          .andExpect(jsonPath("$.result.news[3].title").value("기도제목"))
          .andExpect(jsonPath("$.result.news[3].children[0].children.length()").value(2))
          .andExpect(jsonPath("$.result.duties.length()").value(16))
          .andExpect(jsonPath("$.result.catechismUnits[0].unitNo").value(45))
          .andExpect(jsonPath("$.result.catechismUnits[0].path[0].unitTypeCd").value("PART"))
          .andExpect(jsonPath("$.result.catechismUnits[0].qas.length()").value(4))
          .andExpect(jsonPath("$.result.catechismUnits[0].qas[1].answers.length()").value(3))
          .andExpect(jsonPath("$.result.liturgyTexts[0].title").value("사도신경"));
    }

    @Test
    @DisplayName("등록 → 상세(트리·섬김위원·연결 순서 유지) → 수정(하위 전체 교체) → 삭제")
    void crud() throws Exception {
      String created = admin.perform(post("/api/v1/bulletins").contentType(MediaType.APPLICATION_JSON)
              .content(BULLETIN_BODY.formatted("2026-11-15", 46, "DRAFT")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.bulletinId", notNullValue()))
          .andExpect(jsonPath("$.result.news[0].children[0].children[0].content").value("주일예배"))
          .andExpect(jsonPath("$.result.news[1].children[0].highlightYn").value("Y"))
          .andExpect(jsonPath("$.result.duties[2].roleNm").value("안내"))
          .andExpect(jsonPath("$.result.liturgyTexts[0].title").value("주기도문"))
          .andReturn().getResponse().getContentAsString();
      long id = ((Number) JsonPath.read(created, "$.result.bulletinId")).longValue();

      admin.perform(put("/api/v1/bulletins/" + id).contentType(MediaType.APPLICATION_JSON)
              .content(BULLETIN_BODY.formatted("2026-11-15", 46, "PUBLISHED")
                  .replace("\"liturgyTextIds\": [2, 1]", "\"liturgyTextIds\": []")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.statusCd").value("PUBLISHED"))
          .andExpect(jsonPath("$.result.orders.length()").value(2))
          .andExpect(jsonPath("$.result.liturgyTexts.length()").value(0));

      admin.perform(delete("/api/v1/bulletins/" + id)).andExpect(status().isOk());
      admin.perform(get("/api/v1/bulletins/" + id))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B8001"));
    }

    @Test
    @DisplayName("검증 - 예배일 중복 B8002, 상태 B8003, 단위 B8004, 전례문 B8005, 소식 4단계 B8006, 섬김 중복 B8007")
    void validation() throws Exception {
      String body = BULLETIN_BODY.formatted("2026-11-22", 47, "DRAFT");
      expectError(body.replace("2026-11-22", "2026-11-08"), "B8002");
      expectError(body.replace("\"DRAFT\"", "\"NOPE\""), "B8003");
      expectError(body.replace("\"catechismUnitIds\": [4]", "\"catechismUnitIds\": [999]"), "B8004");
      expectError(body.replace("[2, 1]", "[999]"), "B8005");
      expectError(body.replace("{\"content\": \"주일예배\"}",
          "{\"content\": \"3단계\", \"children\": [{\"content\": \"4단계\"}]}"), "B8006");
      expectError(body.replace("\"2026-11-22\", \"members\": \"나래\"",
          "\"2026-11-15\", \"members\": \"나래\""), "B8007");
    }

    private void expectError(String body, String messageId) throws Exception {
      admin.perform(post("/api/v1/bulletins").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(jsonPath("$.messageId").value(messageId));
    }

    @Test
    @DisplayName("작성 중 주보 - 조회 권한(bulletin.list)만 있으면 목록·상세에서 안 보임, 편집 권한이면 보임")
    void draftVisibility() throws Exception {
      String created = admin.perform(post("/api/v1/bulletins").contentType(MediaType.APPLICATION_JSON)
              .content(BULLETIN_BODY.formatted("2026-11-29", 48, "DRAFT")))
          .andReturn().getResponse().getContentAsString();
      long id = ((Number) JsonPath.read(created, "$.result.bulletinId")).longValue();

      MockMvc viewer = mvc("viewer", "SCOPE_bulletin.list");
      viewer.perform(get("/api/v1/bulletins"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.totalElements").value(1));
      viewer.perform(get("/api/v1/bulletins/" + id)).andExpect(status().isNotFound());
      viewer.perform(post("/api/v1/bulletins").contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isForbidden());

      MockMvc editor = mvc("editor", "SCOPE_bulletin.list", "SCOPE_bulletin.edit");
      editor.perform(get("/api/v1/bulletins"))
          .andExpect(jsonPath("$.result.totalElements").value(2));
      editor.perform(get("/api/v1/bulletins/" + id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("대표사진 - 내용으로 형식 판별(PNG 이름의 HTML 거부), 올리기 → 보기(판별한 Content-Type) → 지우기, 권한")
    void photo() throws Exception {
      byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
      admin.perform(multipart("/api/v1/bulletins/1/photo").file(new MockMultipartFile("file",
              "fake.png", "image/png", "<script>alert(1)</script>".getBytes())))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B8009"));

      MockMvc manager = mvc("manager", "SCOPE_bulletin.list", "SCOPE_bulletin.add");
      manager.perform(multipart("/api/v1/bulletins/1/photo").file(new MockMultipartFile("file",
              "cover.jpg", "image/jpeg", png)))
          .andExpect(status().isCreated());
      admin.perform(get("/api/v1/bulletins/1"))
          .andExpect(jsonPath("$.result.hasPhoto").value(true));
      admin.perform(get("/api/v1/bulletins/1/photo"))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", "image/png"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(content().bytes(png));

      MockMvc viewer = mvc("viewer", "SCOPE_bulletin.list");
      viewer.perform(get("/api/v1/bulletins/1/photo")).andExpect(status().isOk());
      viewer.perform(multipart("/api/v1/bulletins/1/photo").file(new MockMultipartFile("file",
              "cover.png", "image/png", png)))
          .andExpect(status().isForbidden());
      manager.perform(delete("/api/v1/bulletins/1/photo")).andExpect(status().isForbidden());

      admin.perform(delete("/api/v1/bulletins/1/photo")).andExpect(status().isOk());
      admin.perform(get("/api/v1/bulletins/1/photo"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B8011"));
    }
  }

  @Nested
  @DisplayName("교리문답")
  class CatechismTest {

    private static final String UNIT_BODY = """
        {"parentUnitId": 3, "unitTypeCd": "LORDS_DAY", "unitNo": 46, "title": "기도에 관하여",
         "sortOrder": 46, "qas": [
           {"questionNo": 120, "question": "왜 그리스도께서 하나님을 우리 아버지로 부르라 명하셨습니까?",
            "answers": [{"content": "첫째 단락", "proofs": [{"bibleRef": "마 7:9-11"}]},
                        {"content": "둘째 단락"}]},
           {"questionNo": 121, "question": "질문", "answers": [{"content": "답"}]}]}
        """;

    @Test
    @DisplayName("목록 - 하이델베르크 3부 트리, 제45주일에 116~119문")
    void tree() throws Exception {
      admin.perform(get("/api/v1/catechisms"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].catechismCd").value("HEIDELBERG"))
          .andExpect(jsonPath("$.result[0].units.length()").value(3))
          .andExpect(jsonPath("$.result[0].units[2].children[0].unitNo").value(45))
          .andExpect(jsonPath("$.result[0].units[2].children[0].qaCount").value(4))
          .andExpect(jsonPath("$.result[0].units[2].children[0].firstQuestionNo").value(116))
          .andExpect(jsonPath("$.result[0].units[2].children[0].lastQuestionNo").value(119));
    }

    @Test
    @DisplayName("단위 등록(문답·답 단락·근거 성구) → 수정(전체 교체) → 삭제, 상위 경로 포함")
    void unitCrud() throws Exception {
      String created = admin.perform(post("/api/v1/catechisms/1/units")
              .contentType(MediaType.APPLICATION_JSON).content(UNIT_BODY))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.path.length()").value(2))
          .andExpect(jsonPath("$.result.qas[0].answers[0].proofs[0].bibleRef").value("마 7:9-11"))
          .andExpect(jsonPath("$.result.qas[0].answers.length()").value(2))
          .andReturn().getResponse().getContentAsString();
      long unitId = ((Number) JsonPath.read(created, "$.result.unitId")).longValue();

      admin.perform(put("/api/v1/catechisms/units/" + unitId).contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"parentUnitId": 3, "unitTypeCd": "LORDS_DAY", "unitNo": 46, "qas": [
                    {"questionNo": 120, "question": "수정", "answers": [{"content": "답"}]}]}
                  """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.qas.length()").value(1));

      admin.perform(delete("/api/v1/catechisms/units/" + unitId)).andExpect(status().isOk());
      admin.perform(get("/api/v1/catechisms/units/" + unitId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B9003"));
    }

    @Test
    @DisplayName("검증 - 문 번호 중복(다른 단위의 116) B9005, 자기 하위를 상위로 B9006, 하위 있는 단위 삭제 B9007, "
        + "주보에서 쓰는 단위 삭제 B9008, 단위 있는 교리문답 삭제 B9009")
    void validation() throws Exception {
      admin.perform(post("/api/v1/catechisms/1/units").contentType(MediaType.APPLICATION_JSON)
              .content(UNIT_BODY.replace("\"questionNo\": 120", "\"questionNo\": 116")))
          .andExpect(jsonPath("$.messageId").value("B9005"));
      admin.perform(put("/api/v1/catechisms/units/3").contentType(MediaType.APPLICATION_JSON)
              .content("{\"parentUnitId\": 4, \"unitTypeCd\": \"PART\", \"qas\": []}"))
          .andExpect(jsonPath("$.messageId").value("B9006"));
      admin.perform(delete("/api/v1/catechisms/units/3"))
          .andExpect(jsonPath("$.messageId").value("B9007"));
      admin.perform(delete("/api/v1/catechisms/units/4"))
          .andExpect(jsonPath("$.messageId").value("B9008"));
      admin.perform(delete("/api/v1/catechisms/1"))
          .andExpect(jsonPath("$.messageId").value("B9009"));
    }
  }

  @Nested
  @DisplayName("전례문")
  class LiturgyTest {

    @Test
    @DisplayName("목록(화자별 줄) → 등록·수정(줄 교체) → 삭제, 주보에서 쓰는 전례문 삭제 B9503, 화자 코드 오류 B9502")
    void crud() throws Exception {
      admin.perform(get("/api/v1/liturgy-texts"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].lines[0].speakerCd").value("LEADER"))
          .andExpect(jsonPath("$.result[1].versionNm").value("개역한글"));

      String body = """
          {"textTypeCd": "RESPONSIVE", "title": "교독문 1", "useYn": "Y",
           "lines": [{"speakerCd": "LEADER", "content": "인도자"}, {"speakerCd": "PEOPLE", "content": "회중"},
                     {"speakerCd": "ALL", "content": "다함께"}]}
          """;
      String created = admin.perform(post("/api/v1/liturgy-texts")
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.lines.length()").value(3))
          .andReturn().getResponse().getContentAsString();
      long id = ((Number) JsonPath.read(created, "$.result.textId")).longValue();
      admin.perform(put("/api/v1/liturgy-texts/" + id).contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"textTypeCd": "RESPONSIVE", "title": "교독문 1", "useYn": "Y",
                   "lines": [{"speakerCd": "LEADER", "content": "인도자"}, {"content": "화자 없음"}]}
                  """))
          .andExpect(jsonPath("$.result.lines.length()").value(2));
      admin.perform(delete("/api/v1/liturgy-texts/" + id)).andExpect(status().isOk());

      admin.perform(delete("/api/v1/liturgy-texts/1"))
          .andExpect(jsonPath("$.messageId").value("B9503"));
      admin.perform(post("/api/v1/liturgy-texts").contentType(MediaType.APPLICATION_JSON)
              .content(body.replace("\"PEOPLE\"", "\"NOPE\"")))
          .andExpect(jsonPath("$.messageId").value("B9502"));
    }

    @Test
    @DisplayName("인가 - liturgy.list 는 조회만, 등록 403")
    void authorization() throws Exception {
      MockMvc viewer = mvc("viewer", "SCOPE_liturgy.list", "SCOPE_catechism.list");
      viewer.perform(get("/api/v1/liturgy-texts")).andExpect(status().isOk());
      viewer.perform(get("/api/v1/catechisms/units/4"))
          .andExpect(jsonPath("$.result.qas[*].questionNo").value(hasItem(117)));
      viewer.perform(post("/api/v1/liturgy-texts").contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isForbidden());
    }
  }
}
