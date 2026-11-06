package com.flowlog.backend.sample.web;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowlog.backend.global.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 샘플 API 통합 테스트. (H2 + 실제 Spring Context)
 *
 * <p>표준 응답 형식, HTTP Method 별 상태코드/메시지, 오류 처리, 다국어를 함께 검증한다.
 * 초기 데이터는 {@code db/data/00_sample.sql} (샘플1~3) 을 사용한다.
 * 모든 요청은 시스템관리자(admin) JWT 로 인증된 상태로 보낸다. (인증/인가 자체는 SecurityIntegrationTest)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest
@Transactional
class SampleApiIntegrationTest {

  private static final String BASE_URL = "/api/v1/samples";

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
  @DisplayName("목록 조회 - 200, S0002, 표준 응답 필드, 페이징 결과")
  void getSamples() throws Exception {
    mockMvc.perform(get(BASE_URL).param("useYn", "Y"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Trace-Id"))
        .andExpect(jsonPath("$.locale").value("ko_KR"))
        .andExpect(jsonPath("$.timestamp", notNullValue()))
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.messageId").value("S0002"))
        .andExpect(jsonPath("$.messageTitle").value("목록 조회 완료"))
        .andExpect(jsonPath("$.messageContent").value("2건 조회되었습니다."))
        .andExpect(jsonPath("$.path").value(BASE_URL))
        .andExpect(jsonPath("$.result.totalElements").value(2))
        .andExpect(jsonPath("$.result.content[0].name").value("샘플2"));
  }

  @Test
  @DisplayName("목록 조회 - 이름 부분 일치 검색, 와일드카드(%, _)는 문자 그대로 검색")
  void getSamples_nameSearch() throws Exception {
    mockMvc.perform(get(BASE_URL).param("name", "샘플"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalElements").value(3));
    mockMvc.perform(get(BASE_URL).param("name", "플2"))
        .andExpect(jsonPath("$.result.totalElements").value(1))
        .andExpect(jsonPath("$.result.content[0].name").value("샘플2"));
    mockMvc.perform(get(BASE_URL).param("name", "%"))
        .andExpect(jsonPath("$.result.totalElements").value(0));
    mockMvc.perform(get(BASE_URL).param("name", "샘_1"))
        .andExpect(jsonPath("$.result.totalElements").value(0));
  }

  @Test
  @DisplayName("단건 조회 - 200, S0001")
  void getSample() throws Exception {
    mockMvc.perform(get(BASE_URL + "/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messageId").value("S0001"))
        .andExpect(jsonPath("$.path").value(BASE_URL + "/1"))
        .andExpect(jsonPath("$.result.name").value("샘플1"));
  }

  @Test
  @DisplayName("등록 - 201, S0003, Auditing 생성자 = 로그인 사용자(admin)")
  void createSample() throws Exception {
    String body = """
        {"name": "신규 샘플", "description": "설명", "useYn": "Y"}
        """;
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.messageId").value("S0003"))
        .andExpect(jsonPath("$.result.id", notNullValue()))
        .andExpect(jsonPath("$.result.createdBy").value("admin"))
        .andExpect(jsonPath("$.result.createdAt", notNullValue()));
  }

  @Test
  @DisplayName("수정 - 200, S0004")
  void updateSample() throws Exception {
    String body = """
        {"name": "수정된 샘플", "description": "수정", "useYn": "N"}
        """;
    mockMvc.perform(put(BASE_URL + "/1").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messageId").value("S0004"))
        .andExpect(jsonPath("$.result.name").value("수정된 샘플"))
        .andExpect(jsonPath("$.result.useYn").value("N"));
  }

  @Test
  @DisplayName("삭제 - 200, S0005, result null")
  void deleteSample() throws Exception {
    mockMvc.perform(delete(BASE_URL + "/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messageId").value("S0005"))
        .andExpect(jsonPath("$.result").doesNotExist());
  }

  @Test
  @DisplayName("오류 - 존재하지 않는 샘플: 404, B1001, 메시지 인자 치환")
  void getSample_notFound() throws Exception {
    mockMvc.perform(get(BASE_URL + "/9999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.httpStatus").value(404))
        .andExpect(jsonPath("$.messageId").value("B1001"))
        .andExpect(jsonPath("$.messageContent").value("샘플 정보를 찾을 수 없습니다. (ID: 9999)"));
  }

  @Test
  @DisplayName("오류 - 샘플명 중복: 409, B1002")
  void createSample_duplicate() throws Exception {
    String body = """
        {"name": "샘플1", "useYn": "Y"}
        """;
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.messageId").value("B1002"));
  }

  @Test
  @DisplayName("오류 - 입력값 검증 실패: 400, C0002, result 에 필드 오류 목록")
  void createSample_invalidInput() throws Exception {
    String body = """
        {"name": "", "useYn": "X"}
        """;
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("C0002"))
        .andExpect(jsonPath("$.result.length()").value(2));
  }

  @Test
  @DisplayName("오류 - PathVariable 제약조건 위반: 400, C0002")
  void getSample_invalidPathVariable() throws Exception {
    mockMvc.perform(get(BASE_URL + "/0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("C0002"))
        .andExpect(jsonPath("$.result[0].field").value("id"));
  }

  @Test
  @DisplayName("오류 - PathVariable 타입 불일치: 400, C0003")
  void getSample_typeMismatch() throws Exception {
    mockMvc.perform(get(BASE_URL + "/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("C0003"));
  }

  @Test
  @DisplayName("오류 - JSON 형식 오류: 400, C0005")
  void createSample_unreadableBody() throws Exception {
    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messageId").value("C0005"));
  }

  @Test
  @DisplayName("오류 - 지원하지 않는 Method: 405, C0006")
  void methodNotAllowed() throws Exception {
    mockMvc.perform(patch(BASE_URL + "/1"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.messageId").value("C0006"));
  }

  @Test
  @DisplayName("오류 - 존재하지 않는 경로(인가 규칙 범위 내): 404, C0008")
  void pathNotFound() throws Exception {
    mockMvc.perform(get(BASE_URL + "/1/unknown"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.messageId").value("C0008"));
  }

  @Test
  @DisplayName("다국어 - Accept-Language: en-US 이면 영문 메시지, locale en_US")
  void locale_enUs() throws Exception {
    mockMvc.perform(get(BASE_URL + "/9999").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.locale").value("en_US"))
        .andExpect(jsonPath("$.messageTitle").value("Sample not found"));
  }

  @Test
  @DisplayName("다국어 - 미지원 언어면 기본값 ko_KR")
  void locale_unsupported_fallbackToKorean() throws Exception {
    mockMvc.perform(get(BASE_URL + "/1").header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("ko_KR"))
        .andExpect(jsonPath("$.messageTitle").value("조회 완료"));
  }
}
