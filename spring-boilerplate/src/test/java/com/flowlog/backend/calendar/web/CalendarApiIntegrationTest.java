package com.flowlog.backend.calendar.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowlog.backend.global.web.TraceIdFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 캘린더 API 통합 테스트. (H2 + 초기 데이터 db/data/03_calendar.sql)
 *
 * <p>시스템관리자로 기능을 검증하고, 인가는 scope 별로 확인한다.
 *
 * @author flowlog
 * @since 2026-11-07
 */
@SpringBootTest
@Transactional
class CalendarApiIntegrationTest {

  private static final String BASE_URL = "/api/v1/calendar/events";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private TraceIdFilter traceIdFilter;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(traceIdFilter)
        .apply(springSecurity())
        .defaultRequest(get("/").with(jwt()
            .jwt(token -> token.subject("admin"))
            .authorities(new SimpleGrantedAuthority("ROLE_SYS_ADMIN"))))
        .build();
  }

  @Test
  @DisplayName("기간 조회 - 2026년 11월 : 매년 반복 생일·결혼기념일이 올해 날짜로, 나이·주년 포함, 날짜순")
  void getOccurrences_november() throws Exception {
    mockMvc.perform(get(BASE_URL).param("from", "2026-11-01").param("to", "2026-11-30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(3))
        .andExpect(jsonPath("$.result[0].title").value("김민준 생일"))
        .andExpect(jsonPath("$.result[0].startDt").value("2026-11-12"))
        .andExpect(jsonPath("$.result[0].yearsSince").value(36))
        .andExpect(jsonPath("$.result[0].originStartDt").value("1990-11-12"))
        .andExpect(jsonPath("$.result[1].eventTypeCd").value("WEDDING"))
        .andExpect(jsonPath("$.result[1].yearsSince").value(6))
        .andExpect(jsonPath("$.result[2].startDt").value("2026-11-27"));
  }

  @Test
  @DisplayName("기간 조회 - 반복 안 하는 여러 날 휴일은 원래 기간, 다음 해에는 나오지 않음")
  void getOccurrences_multiDayOnce() throws Exception {
    mockMvc.perform(get(BASE_URL).param("from", "2026-09-01").param("to", "2026-09-30"))
        .andExpect(jsonPath("$.result[?(@.title == '추석 연휴')].startDt").value(hasItem("2026-09-24")))
        .andExpect(jsonPath("$.result[?(@.title == '추석 연휴')].endDt").value(hasItem("2026-09-26")));
    mockMvc.perform(get(BASE_URL).param("from", "2027-09-01").param("to", "2027-09-30"))
        .andExpect(jsonPath("$.result.length()").value(0));
  }

  @Test
  @DisplayName("기간 조회 - 종료일이 시작일보다 빠르거나 400일 초과면 400 B7005, 필수값 누락 400")
  void getOccurrences_invalidRange() throws Exception {
    mockMvc.perform(get(BASE_URL).param("from", "2026-11-30").param("to", "2026-11-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("B7005"));
    mockMvc.perform(get(BASE_URL).param("from", "2026-01-01").param("to", "2027-12-31"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("B7005"));
    mockMvc.perform(get(BASE_URL).param("from", "2026-11-01"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("등록·단건·수정·삭제 - 매년 반복 결혼기념일 등록 후 다음 해에도 조회됨, 삭제 후 404 B7001")
  void crud() throws Exception {
    // 등록 (종료일 생략 = 시작일)
    String body = """
        {"eventTypeCd": "WEDDING", "title": "  이서연 결혼기념일 ", "startDt": "2024-05-18",
         "repeatYearlyYn": "Y", "memo": " 꽃 "}
        """;
    String created = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.messageId").value("S0003"))
        .andExpect(jsonPath("$.result.id", notNullValue()))
        .andExpect(jsonPath("$.result.title").value("이서연 결혼기념일"))
        .andExpect(jsonPath("$.result.endDt").value("2024-05-18"))
        .andExpect(jsonPath("$.result.memo").value("꽃"))
        .andExpect(jsonPath("$.result.createdBy").value("admin"))
        .andReturn().getResponse().getContentAsString();
    long id = ((Number) JsonPath.read(created, "$.result.id")).longValue();

    mockMvc.perform(get(BASE_URL).param("from", "2027-05-01").param("to", "2027-05-31"))
        .andExpect(jsonPath("$.result[?(@.eventId == " + id + ")].yearsSince").value(hasItem(3)));

    // 수정 : 반복 해제
    String update = """
        {"eventTypeCd": "ETC", "title": "가족 모임", "startDt": "2026-12-05", "endDt": "2026-12-06",
         "repeatYearlyYn": "N"}
        """;
    mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.eventTypeCd").value("ETC"))
        .andExpect(jsonPath("$.result.repeatYearlyYn").value("N"))
        .andExpect(jsonPath("$.result.memo").doesNotExist());
    mockMvc.perform(get(BASE_URL + "/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.endDt").value("2026-12-06"));

    // 삭제
    mockMvc.perform(delete(BASE_URL + "/" + id))
        .andExpect(status().isOk());
    mockMvc.perform(get(BASE_URL + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageId").value("B7001"));
  }

  @Test
  @DisplayName("연도 모름(yearKnownYn=N) - 매년 반복은 하되 나이·주년(yearsSince)은 0")
  void yearUnknown() throws Exception {
    String created = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"eventTypeCd": "BIRTHDAY", "title": "연도 모름 생일", "startDt": "2026-03-07",
                 "repeatYearlyYn": "Y", "yearKnownYn": "N"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.yearKnownYn").value("N"))
        .andReturn().getResponse().getContentAsString();
    long id = ((Number) JsonPath.read(created, "$.result.id")).longValue();

    mockMvc.perform(get(BASE_URL).param("from", "2030-03-01").param("to", "2030-03-31"))
        .andExpect(jsonPath("$.result[?(@.eventId == " + id + ")].startDt").value(hasItem("2030-03-07")))
        .andExpect(jsonPath("$.result[?(@.eventId == " + id + ")].yearsSince").value(hasItem(0)));
  }

  @Test
  @DisplayName("등록 검증 -유형 코드 오류 B7002, 종료일 < 시작일 B7003, 반복 일정 31일 초과 B7004, 필수값 400")
  void create_validation() throws Exception {
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("""
            {"eventTypeCd": "NOPE", "title": "x", "startDt": "2026-11-01", "repeatYearlyYn": "N"}
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("B7002"));
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("""
            {"eventTypeCd": "ETC", "title": "x", "startDt": "2026-11-02", "endDt": "2026-11-01",
             "repeatYearlyYn": "N"}
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("B7003"));
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("""
            {"eventTypeCd": "HOLIDAY", "title": "x", "startDt": "2026-11-01", "endDt": "2026-12-15",
             "repeatYearlyYn": "Y"}
            """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("B7004"));
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("""
            {"eventTypeCd": "ETC", "title": " ", "repeatYearlyYn": "X"}
            """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("인가 - calendar.list 만 있으면 조회만, 등록·수정·삭제는 403. scope 없으면 조회도 403")
  void authorization() throws Exception {
    MockMvc viewer = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .defaultRequest(get("/").with(jwt()
            .jwt(token -> token.subject("viewer"))
            .authorities(new SimpleGrantedAuthority("SCOPE_calendar.list"))))
        .build();
    viewer.perform(get(BASE_URL).param("from", "2026-11-01").param("to", "2026-11-30"))
        .andExpect(status().isOk());
    viewer.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    viewer.perform(put(BASE_URL + "/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    viewer.perform(delete(BASE_URL + "/1"))
        .andExpect(status().isForbidden());

    MockMvc other = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .defaultRequest(get("/").with(jwt()
            .jwt(token -> token.subject("other"))
            .authorities(new SimpleGrantedAuthority("SCOPE_board.list"))))
        .build();
    other.perform(get(BASE_URL).param("from", "2026-11-01").param("to", "2026-11-30"))
        .andExpect(status().isForbidden());
  }
}
