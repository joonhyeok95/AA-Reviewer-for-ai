package com.flowlog.backend.notice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowlog.backend.code.domain.model.Code;
import com.flowlog.backend.code.domain.model.CodeLang;
import com.flowlog.backend.code.domain.repository.CodeLangRepository;
import com.flowlog.backend.code.domain.repository.CodeRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 공지사항 API 통합 테스트. (다국어 NT-60~66, 지원 언어, 인사 마스터, 첨부, 권한)
 *
 * <p>사용자 언어 : admin=ko, tx.manager=en, tx.viewer=미설정(→ Accept-Language → 기본 ko)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest
@Transactional
class NoticeApiIntegrationTest {

  private static final String URL = "/api/v1/notices";

  private static final Map<String, String> SUBJECTS = Map.of(
      "admin", "00000000-0000-4000-8000-000000000001",
      "tx.manager", "00000000-0000-4000-8000-000000000003",
      "tx.viewer", "00000000-0000-4000-8000-000000000004");

  private static final String ALL_NOTICE = "notice.list notice.add notice.edit notice.del";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private CodeRepository codeRepository;

  @Autowired
  private CodeLangRepository codeLangRepository;

  @Autowired
  private CacheManager cacheManager;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
            .springSecurity())
        .build();
    clearCaches();
  }

  @AfterEach
  void tearDown() {
    clearCaches();
  }

  private void clearCaches() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  /** 사용자 토큰 (scope 는 DB 권한과 교집합). */
  private String token(String username, String scope) {
    String value = "token-" + username + "-" + scope.hashCode();
    Jwt jwt = Jwt.withTokenValue(value).header("alg", "RS256")
        .subject(SUBJECTS.get(username))
        .issuer("http://localhost:7000/realms/flowlog").audience(List.of("flowlog-api"))
        .claims(c -> c.putAll(Map.of("preferred_username", username, "azp", "flowlog-bff",
            "scope", scope)))
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    given(jwtDecoder.decode(value)).willReturn(jwt);
    return "Bearer " + value;
  }

  private ResultActions perform(
      AbstractMockHttpServletRequestBuilder<?> request, String user, String scope)
      throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token(user, scope)));
  }

  private static String body(String i18nJson, String extra) {
    return "{\"categoryCd\":\"GENERAL\",\"i18n\":" + i18nJson + extra + "}";
  }

  private Long create(String json) throws Exception {
    String response = perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json),
        "admin", ALL_NOTICE)
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(response, "$.result.noticeId")).longValue();
  }

  @Nested
  @DisplayName("지원 언어 (LANG 공통코드 기반 - 하드코딩 없음)")
  class LangTest {

    @Test
    @DisplayName("언어 목록 = LANG 코드(USE_YN=Y) : ko/en, 에디터 언어팩 = ATTR1, 언어명은 사용자 언어")
    void langs() throws Exception {
      perform(get("/api/v1/langs"), "tx.manager", "openid")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].langCd").value("ko"))
          .andExpect(jsonPath("$.result[0].langNm").value("Korean"))
          .andExpect(jsonPath("$.result[0].editorLocale").value("ko-KR"))
          .andExpect(jsonPath("$.result[0].badge").value("KO"))
          .andExpect(jsonPath("$.result[0].defaultLang").value(true))
          .andExpect(jsonPath("$.result[1].langCd").value("en"))
          .andExpect(jsonPath("$.result[1].editorLocale").value("en-US"));
    }

    @Test
    @DisplayName("LANG 코드에 ja 를 추가하면 코드 수정 없이 언어 목록·공지 입력에 바로 반영")
    void addLanguageWithoutCodeChange() throws Exception {
      codeRepository.saveAndFlush(Code.builder().groupCd("LANG").code("ja").sortOrder(3)
          .attr1("ja-JP").useYn("Y").build());
      codeLangRepository.saveAndFlush(new CodeLang("LANG", "ja", "ko", "일본어"));
      clearCaches();

      perform(get("/api/v1/langs"), "admin", "openid")
          .andExpect(jsonPath("$.result[2].langCd").value("ja"))
          .andExpect(jsonPath("$.result[2].langNm").value("일본어"))
          .andExpect(jsonPath("$.result[2].editorLocale").value("ja-JP"));
      Long id = create(body(
          "[{\"langCd\":\"ja\",\"title\":\"お知らせ\",\"contentHtml\":\"<p>本文</p>\"}]", ""));
      perform(get(URL + "/" + id), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.displayLangCd").value("ja"))
          .andExpect(jsonPath("$.result.langs[0].badge").value("JA"));
    }

    @Test
    @DisplayName("사용자 언어 변경 - 이후 조회 언어에 반영, 지원하지 않는 언어는 B3002")
    void changeUserLang() throws Exception {
      perform(put("/api/v1/users/me/lang").contentType(MediaType.APPLICATION_JSON)
          .content("{\"langCd\":\"en\"}"), "admin", "openid")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.langCd").value("en"));
      perform(get(URL + "/1"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.displayLangCd").value("en"));

      perform(put("/api/v1/users/me/lang").contentType(MediaType.APPLICATION_JSON)
          .content("{\"langCd\":\"xx\"}"), "admin", "openid")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B3002"));
    }
  }

  @Nested
  @DisplayName("등록 규칙 (NT-60 ~ NT-62)")
  class CreateTest {

    @Test
    @DisplayName("영어만 입력 - 대표 언어 미지정 시 처음 입력한 언어(en)")
    void englishOnly_baseLangIsFirst() throws Exception {
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"en\",\"title\":\"Hello\",\"contentHtml\":\"<p>World</p>\"},"
                  + "{\"langCd\":\"ko\",\"title\":\"\",\"contentHtml\":\"\"}]", "")),
          "admin", ALL_NOTICE)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.messageId").value("S0003"))
          .andExpect(jsonPath("$.result.baseLangCd").value("en"))
          .andExpect(jsonPath("$.result.i18n.length()").value(1))
          .andExpect(jsonPath("$.result.mailStatusCd").value("NONE"));
    }

    @Test
    @DisplayName("둘 다 입력 + 대표 언어 지정(en)")
    void bothLanguages_explicitBase() throws Exception {
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"ko\",\"title\":\"안녕\",\"contentHtml\":\"<p>본문</p>\"},"
                  + "{\"langCd\":\"en\",\"title\":\"Hi\",\"contentHtml\":\"<p>Body</p>\"}]",
              ",\"baseLangCd\":\"en\"")), "admin", ALL_NOTICE)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.baseLangCd").value("en"))
          .andExpect(jsonPath("$.result.i18n[0].langCd").value("ko"))
          .andExpect(jsonPath("$.result.i18n[1].langCd").value("en"));
    }

    @Test
    @DisplayName("NT-61 - 어떤 언어도 제목·본문이 없으면 B2002, 제목만 있으면 B2003")
    void contentRequired() throws Exception {
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"ko\",\"title\":\" \",\"contentHtml\":\"<p></p>\"}]", "")),
          "admin", ALL_NOTICE)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B2002"));
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"ko\",\"title\":\"제목만\",\"contentHtml\":\"\"}]", "")),
          "admin", ALL_NOTICE)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B2003"));
    }

    @Test
    @DisplayName("지원하지 않는 언어 B2004, 입력하지 않은 언어를 대표 언어로 지정 B2006")
    void invalidLanguage() throws Exception {
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"fr\",\"title\":\"Bonjour\",\"contentHtml\":\"<p>x</p>\"}]", "")),
          "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.messageId").value("B2004"));
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"ko\",\"title\":\"제목\",\"contentHtml\":\"<p>x</p>\"}]",
              ",\"baseLangCd\":\"en\"")), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.messageId").value("B2006"));
    }

    @Test
    @DisplayName("본문 HTML 정화 - script/이벤트 핸들러/javascript: 링크 제거, 서식은 유지")
    void sanitizeHtml() throws Exception {
      Long id = create(body("[{\"langCd\":\"ko\",\"title\":\"XSS\",\"contentHtml\":"
          + "\"<p onclick='x()'>안전<b>굵게</b></p><script>alert(1)</script>"
          + "<a href='javascript:alert(1)'>링크</a><img src='https://a.b/c.png' onerror='x()'>\"}]",
          ""));

      perform(get(URL + "/" + id), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.contentHtml", containsString("<b>굵게</b>")))
          .andExpect(jsonPath("$.result.contentHtml", not(containsString("script"))))
          .andExpect(jsonPath("$.result.contentHtml", not(containsString("onclick"))))
          .andExpect(jsonPath("$.result.contentHtml", not(containsString("onerror"))))
          .andExpect(jsonPath("$.result.contentHtml", not(containsString("javascript"))));
    }

    @Test
    @DisplayName("메일 대상 지정 발송 - 대상 없으면 B2009, 인사 마스터에 없는 부서 B2010, 정상이면 READY")
    void mailTargets() throws Exception {
      String i18n = "[{\"langCd\":\"ko\",\"title\":\"메일\",\"contentHtml\":\"<p>x</p>\"}]";
      perform(post(URL).contentType(MediaType.APPLICATION_JSON)
          .content(body(i18n, ",\"mailSendTypeCd\":\"TARGET\"")), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.messageId").value("B2009"));
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(i18n,
              ",\"mailSendTypeCd\":\"TARGET\",\"mailTargets\":[{\"targetType\":\"DEPT\","
                  + "\"targetCd\":\"NO_DEPT\"}]")), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.messageId").value("B2010"));
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(i18n,
              ",\"mailSendTypeCd\":\"TARGET\",\"mailTargets\":[{\"targetType\":\"WORKPLACE\","
                  + "\"targetCd\":\"SUWON\"}]")), "admin", ALL_NOTICE)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.mailStatusCd").value("READY"))
          .andExpect(jsonPath("$.result.mailTargets[0].targetNm").value("수원 사업장"));
    }

    @Test
    @DisplayName("팝업 사용 시 기간 필수 (B2008), 분류 코드 오류 (B2007)")
    void popupAndCodes() throws Exception {
      String i18n = "[{\"langCd\":\"ko\",\"title\":\"팝업\",\"contentHtml\":\"<p>x</p>\"}]";
      perform(post(URL).contentType(MediaType.APPLICATION_JSON)
          .content(body(i18n, ",\"popupYn\":\"Y\"")), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.messageId").value("B2008"));
      perform(post(URL).contentType(MediaType.APPLICATION_JSON)
          .content("{\"categoryCd\":\"NOPE\",\"i18n\":" + i18n + "}"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.messageId").value("B2007"));
    }
  }

  @Nested
  @DisplayName("조회 언어 / 배지 / 검색 (NT-64 ~ NT-66)")
  class ReadTest {

    @Test
    @DisplayName("NT-64 영어 사용자 목록 - 영어 버전 우선, 한국어만 있는 공지는 대표 언어(ko)로 대체 표시")
    void englishUserList() throws Exception {
      perform(get(URL), "tx.manager", "notice.list")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.content[0].noticeId").value(3))
          .andExpect(jsonPath("$.result.content[0].title").value("Global town hall"))
          .andExpect(jsonPath("$.result.content[1].noticeId").value(2))
          .andExpect(jsonPath("$.result.content[1].displayLangCd").value("ko"))
          .andExpect(jsonPath("$.result.content[1].fallback").value(true))
          .andExpect(jsonPath("$.result.content[2].title").value("System launch"))
          .andExpect(jsonPath("$.result.content[2].categoryNm").value("General"))
          .andExpect(jsonPath("$.result.content[2].langs[*].badge").value(contains("KO", "EN")));
    }

    @Test
    @DisplayName("NT-64 언어 미설정 사용자 - Accept-Language(ko) → 한국어 버전, 영어만 있는 공지는 대체")
    void koreanUserList() throws Exception {
      perform(get(URL).header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR"), "tx.viewer", "notice.list")
          .andExpect(jsonPath("$.result.content[0].displayLangCd").value("en"))
          .andExpect(jsonPath("$.result.content[0].fallback").value(true))
          .andExpect(jsonPath("$.result.content[2].title").value("시스템 오픈 안내"))
          .andExpect(jsonPath("$.result.content[2].categoryNm").value("일반"));
    }

    @Test
    @DisplayName("NT-66 검색 - 사용자 언어로 표시되는 버전 기준 (대표 언어로 대체 표시되는 공지 포함)")
    void searchByDisplayedVersion() throws Exception {
      perform(get(URL).param("keyword", "정기 점검"), "tx.manager", "notice.list")
          .andExpect(jsonPath("$.result.totalElements").value(1))
          .andExpect(jsonPath("$.result.content[0].noticeId").value(2));
      perform(get(URL).param("keyword", "LAUNCH"), "tx.manager", "notice.list")
          .andExpect(jsonPath("$.result.totalElements").value(1));
      // 한국어 사용자에게는 1번 공지가 한국어 버전으로 표시되므로 영어 제목으로는 검색되지 않음
      perform(get(URL).param("keyword", "launch"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.totalElements").value(0));
      perform(get(URL).param("keyword", "100%"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    @DisplayName("NT-65 상세 - 제공 언어 배지 + 언어 전환(lang=en), 없는 언어 요청은 사용자 언어/대표 언어로")
    void detailLanguageSwitch() throws Exception {
      perform(get(URL + "/1"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.displayLangCd").value("ko"))
          .andExpect(jsonPath("$.result.langs[*].badge").value(contains("KO", "EN")));
      perform(get(URL + "/1").param("lang", "en"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.title").value("System launch"))
          .andExpect(jsonPath("$.result.fallback").value(false));
      perform(get(URL + "/2").param("lang", "en"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.displayLangCd").value("ko"))
          .andExpect(jsonPath("$.result.fallback").value(true));
    }

    @Test
    @DisplayName("예약 공지(게시 전) - 일반 사용자 목록/상세 제외(404), 관리자는 includeScheduled 로 조회")
    void scheduledNotice() throws Exception {
      perform(get(URL), "tx.viewer", "notice.list")
          .andExpect(jsonPath("$.result.content[*].noticeId", not(hasItem(4))));
      perform(get(URL + "/4"), "tx.viewer", "notice.list")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B2001"));
      perform(get(URL).param("includeScheduled", "true"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.content[0].noticeId").value(4))
          .andExpect(jsonPath("$.result.content[0].scheduled").value(true));
    }

    @Test
    @DisplayName("조회수 - 같은 사용자는 하루 1회만 증가")
    void viewCountOncePerDay() throws Exception {
      perform(get(URL + "/2"), "tx.viewer", "notice.list")
          .andExpect(jsonPath("$.result.viewCnt").value(1));
      perform(get(URL + "/2"), "tx.viewer", "notice.list")
          .andExpect(jsonPath("$.result.viewCnt").value(1));
      perform(get(URL + "/2"), "tx.manager", "notice.list")
          .andExpect(jsonPath("$.result.viewCnt").value(2));
    }

    @Test
    @DisplayName("팝업 공지 - 기간 내 팝업만")
    void popups() throws Exception {
      perform(get(URL + "/popups"), "tx.manager", "notice.list")
          .andExpect(jsonPath("$.result.length()").value(1))
          .andExpect(jsonPath("$.result[0].title").value("System launch"));
    }
  }

  @Nested
  @DisplayName("수정 / 삭제 / 권한")
  class ManageTest {

    @Test
    @DisplayName("수정 - 언어 버전 교체 (영어 추가, 대표 언어 변경), 수정 화면에 전체 버전")
    void update() throws Exception {
      perform(put(URL + "/2").contentType(MediaType.APPLICATION_JSON).content(body(
              "[{\"langCd\":\"ko\",\"title\":\"정기 점검 안내\",\"contentHtml\":\"<p>점검</p>\"},"
                  + "{\"langCd\":\"en\",\"title\":\"Maintenance\",\"contentHtml\":\"<p>m</p>\"}]",
              ",\"baseLangCd\":\"en\"")), "admin", ALL_NOTICE)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.baseLangCd").value("en"))
          .andExpect(jsonPath("$.result.i18n.length()").value(2));
      perform(get(URL + "/2/edit"), "admin", ALL_NOTICE)
          .andExpect(jsonPath("$.result.i18n[1].title").value("Maintenance"));
    }

    @Test
    @DisplayName("권한 - 조회 전용 403(등록), 매니저 등록 가능·삭제 불가, 관리자 삭제 후 404")
    void permissions() throws Exception {
      String json = body("[{\"langCd\":\"ko\",\"title\":\"t\",\"contentHtml\":\"<p>c</p>\"}]", "");
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json), "tx.viewer",
          ALL_NOTICE)
          .andExpect(status().isForbidden());
      perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json), "tx.manager",
          ALL_NOTICE)
          .andExpect(status().isCreated());
      perform(delete(URL + "/2"), "tx.manager", ALL_NOTICE)
          .andExpect(status().isForbidden());
      perform(get(URL + "/2/edit"), "tx.viewer", ALL_NOTICE)
          .andExpect(status().isForbidden());

      perform(delete(URL + "/2"), "admin", ALL_NOTICE).andExpect(status().isOk());
      perform(get(URL + "/2"), "admin", ALL_NOTICE).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("첨부파일 (게시물 공통)")
  class FileTest {

    @Test
    @DisplayName("업로드 → 상세에 표시 → 다운로드(attachment, 원본 파일명) → 삭제")
    void uploadDownloadDelete() throws Exception {
      MockMultipartFile file = new MockMultipartFile("files", "안내문.txt", "text/plain",
          "hello".getBytes());
      String response = perform(multipart(URL + "/1/files").file(file), "admin", ALL_NOTICE)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result[0].orgFileNm").value("안내문.txt"))
          .andExpect(jsonPath("$.result[0].fileExt").value("txt"))
          .andReturn().getResponse().getContentAsString();
      long fileId = ((Number) JsonPath.read(response, "$.result[0].fileId")).longValue();

      perform(get(URL + "/1"), "tx.viewer", "notice.list")
          .andExpect(jsonPath("$.result.files[0].fileId").value(fileId));
      perform(get(URL + "/1/files/" + fileId), "tx.viewer", "notice.list")
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
          .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
              containsString("UTF-8''%EC%95%88%EB%82%B4%EB%AC%B8.txt")))
          .andExpect(content().bytes("hello".getBytes()));
      // 다른 공지 ID 로는 접근 불가
      perform(get(URL + "/2/files/" + fileId), "tx.viewer", "notice.list")
          .andExpect(status().isNotFound());

      perform(delete(URL + "/1/files/" + fileId), "admin", ALL_NOTICE)
          .andExpect(status().isOk());
      perform(get(URL + "/1/files/" + fileId), "admin", ALL_NOTICE)
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("허용되지 않은 확장자(.exe, 경로 포함 이름) 거부 - B2012")
    void rejectExtension() throws Exception {
      MockMultipartFile file = new MockMultipartFile("files", "../../evil.exe",
          "application/octet-stream", "MZ".getBytes());
      perform(multipart(URL + "/1/files").file(file), "admin", ALL_NOTICE)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B2012"));
    }
  }

  @Nested
  @DisplayName("인사 마스터 조직도 / 공통코드")
  class OrgTest {

    @Test
    @DisplayName("조직도 - 사업장별 부서 트리, 영어 사용자에게 영문 명칭 (영문명 없으면 국문)")
    void orgTreeEnglish() throws Exception {
      perform(get("/api/v1/org/tree"), "tx.manager", "openid")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].workplaceNm").value("Seoul Headquarters"))
          .andExpect(jsonPath("$.result[0].depts[0].deptNm").value("Headquarters"))
          .andExpect(jsonPath("$.result[0].depts[0].children[*].deptCd")
              .value(contains("TX_TEAM", "WS_TEAM")))
          .andExpect(jsonPath("$.result[1].workplaceCd").value("SUWON"))
          .andExpect(jsonPath("$.result[1].depts[0].deptNm").value("Innovation TF"))
          .andExpect(jsonPath("$.result[1].depts[0].children[0].deptNm").value("혁신TF 연구실"));
    }

    @Test
    @DisplayName("공통코드 목록 - 사용자 언어 코드명")
    void codes() throws Exception {
      perform(get("/api/v1/codes/NOTICE_CATEGORY"), "admin", "openid")
          .andExpect(jsonPath("$.result[0].code").value("GENERAL"))
          .andExpect(jsonPath("$.result[0].codeNm").value("일반"));
    }
  }
}
