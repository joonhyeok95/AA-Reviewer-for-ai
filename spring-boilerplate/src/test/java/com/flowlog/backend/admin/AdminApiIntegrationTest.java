package com.flowlog.backend.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 관리 API 통합 테스트. (공통코드 관리 / 메뉴 관리 / 권한 관리)
 *
 * <ul>
 *   <li>API 권한 = IdP scope ∩ DB 업무 권한 : 관리 메뉴(12 code, 13 menu, 14 role)의 TB_ROLE_MENU</li>
 *   <li>관리 변경은 권한·공통코드 캐시를 비워 다음 요청부터 반영된다</li>
 *   <li>시스템관리자 권한은 시스템관리자만 변경·부여 (위임받은 관리자도 불가)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@SpringBootTest
@Transactional
class AdminApiIntegrationTest {

  private static final Map<String, String> SUBJECTS = Map.of(
      "admin", "00000000-0000-4000-8000-000000000001",
      "tx.master", "00000000-0000-4000-8000-000000000002",
      "tx.viewer", "00000000-0000-4000-8000-000000000004",
      "emp01", "00000000-0000-4000-8000-000000000005",
      "partner01", "00000000-0000-4000-8000-000000000006");

  private static final String ALL_ADMIN = "code.list code.add code.edit code.del "
      + "menu.list menu.add menu.edit menu.del role.list role.add role.edit role.del "
      + "sample.list notice.list";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private CacheManager cacheManager;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(SecurityMockMvcConfigurers.springSecurity())
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

  private String token(String username, String scope) {
    String value = "admin-test-" + username + "-" + scope.hashCode();
    Jwt jwt = Jwt.withTokenValue(value).header("alg", "RS256")
        .subject(SUBJECTS.get(username))
        .issuer("http://localhost:7000/realms/flowlog").audience(List.of("flowlog-api"))
        .claims(c -> c.putAll(Map.of("preferred_username", username, "azp", "flowlog-bff",
            "scope", scope)))
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    given(jwtDecoder.decode(value)).willReturn(jwt);
    return "Bearer " + value;
  }

  private ResultActions as(String user, String scope,
      AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token(user, scope)));
  }

  private ResultActions admin(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
    return as("admin", ALL_ADMIN, request);
  }

  private static AbstractMockHttpServletRequestBuilder<?> json(
      AbstractMockHttpServletRequestBuilder<?> request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  /** JSON 경로의 숫자 (필터 경로처럼 배열이면 첫 번째). */
  private static long idOf(ResultActions result, String path) throws Exception {
    Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    if (value instanceof List<?> list) {
      value = list.get(0);
    }
    return ((Number) value).longValue();
  }

  // ==================================================================== 공통코드

  @Nested
  @DisplayName("공통코드 관리")
  class CodeAdminTest {

    private static final String GROUPS = "/api/v1/code-groups";

    @Test
    @DisplayName("그룹 목록 - 미사용 포함, 언어별 그룹명, 시스템 여부, 코드 수")
    void groups() throws Exception {
      admin(get(GROUPS))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[?(@.groupCd == 'LANG')].systemYn").value(hasItem("Y")))
          .andExpect(jsonPath("$.result[?(@.groupCd == 'LANG')].codeCnt").value(hasItem(2)))
          .andExpect(jsonPath("$.result[?(@.groupCd == 'NOTICE_TAG')].names.en")
              .value(hasItem("Notice Tag")));
    }

    @Test
    @DisplayName("그룹·코드 등록 → 사용자 코드 API 에 즉시 반영 (캐시 삭제), 영문명 없으면 기본 언어로 표시")
    void createGroupAndCode_reflectedImmediately() throws Exception {
      admin(get("/api/v1/codes/TICKET_TYPE")).andExpect(jsonPath("$.result.length()").value(0));

      admin(json(post(GROUPS), """
          {"groupCd":"TICKET_TYPE","names":{"ko":"문의 유형","en":"Ticket type"},
           "sortOrder":20,"useYn":"Y"}"""))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.systemYn").value("N"));
      admin(json(post(GROUPS + "/TICKET_TYPE/codes"), """
          {"code":"BUG","names":{"ko":"오류","en":"Bug"},"sortOrder":1,"useYn":"Y"}"""))
          .andExpect(status().isCreated());
      admin(json(post(GROUPS + "/TICKET_TYPE/codes"), """
          {"code":"ETC","names":{"ko":"기타","en":" "},"sortOrder":2,"attr1":"x","useYn":"Y"}"""))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.names.en").doesNotExist());

      as("admin", ALL_ADMIN, get("/api/v1/codes/TICKET_TYPE")
          .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR"))
          .andExpect(jsonPath("$.result[*].code").value(org.hamcrest.Matchers.contains("BUG",
              "ETC")));
      admin(get(GROUPS + "/TICKET_TYPE/codes"))
          .andExpect(jsonPath("$.result.length()").value(2))
          .andExpect(jsonPath("$.result[1].attr1").value("x"));
    }

    @Test
    @DisplayName("이름 검증 - 기본 언어(ko) 이름 필수 B4002, 사용하지 않는 언어 B4001, 그룹코드 중복 B4004")
    void validation() throws Exception {
      admin(json(post(GROUPS), """
          {"groupCd":"NO_KO","names":{"en":"Only English"},"sortOrder":1,"useYn":"Y"}"""))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B4002"));
      admin(json(post(GROUPS), """
          {"groupCd":"BAD_LANG","names":{"ko":"이름","fr":"nom"},"sortOrder":1,"useYn":"Y"}"""))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B4001"));
      admin(json(post(GROUPS), """
          {"groupCd":"LANG","names":{"ko":"언어"},"sortOrder":1,"useYn":"Y"}"""))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B4004"));
      admin(json(post(GROUPS), """
          {"groupCd":"lower_case","names":{"ko":"x"},"sortOrder":1,"useYn":"Y"}"""))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("C0002"));
    }

    @Test
    @DisplayName("시스템 그룹 삭제 불가 B4007, 시스템 그룹의 코드 삭제 불가 B4008")
    void systemGroupProtected() throws Exception {
      admin(delete(GROUPS + "/MAIL_STATUS"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B4007"));
      admin(delete(GROUPS + "/MAIL_STATUS/codes/FAIL"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B4008"));
    }

    @Test
    @DisplayName("언어(LANG) - 마지막 사용 중 언어는 미사용 불가 B4009, 미사용 언어는 목록에서 빠지고 그 언어 이름은 보존")
    void lastLangRequired() throws Exception {
      admin(json(put(GROUPS + "/LANG/codes/en"), """
          {"code":"en","names":{"ko":"영어","en":"English"},"sortOrder":2,"attr1":"en-US",
           "useYn":"N"}"""))
          .andExpect(status().isOk());
      admin(get("/api/v1/langs")).andExpect(jsonPath("$.result.length()").value(1));

      admin(json(put(GROUPS + "/LANG/codes/ko"), """
          {"code":"ko","names":{"ko":"한국어","en":"Korean"},"sortOrder":1,"attr1":"ko-KR",
           "useYn":"N"}"""))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B4009"));
      // en 이 미사용이어도 저장된 영문 이름은 지워지지 않는다 (요청의 en 값은 무시)
      admin(json(put(GROUPS + "/NOTICE_TAG"), """
          {"groupCd":"NOTICE_TAG","names":{"ko":"태그"},"sortOrder":7,"useYn":"Y"}"""))
          .andExpect(status().isOk());
      admin(get(GROUPS)).andExpect(jsonPath("$.result[?(@.groupCd == 'NOTICE_TAG')].names.en")
          .value(hasItem("Notice Tag")));
    }

    @Test
    @DisplayName("일반 그룹 삭제 - 코드·언어별 이름 포함 삭제")
    void deleteGroup() throws Exception {
      admin(delete(GROUPS + "/NOTICE_TAG")).andExpect(status().isOk());
      admin(get(GROUPS + "/NOTICE_TAG/codes"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B4003"));
    }

    @Test
    @DisplayName("권한 - DB 업무 권한 없는 사용자는 scope 가 있어도 403")
    void forbidden() throws Exception {
      as("tx.viewer", "code.list", get(GROUPS)).andExpect(status().isForbidden());
    }
  }

  // ==================================================================== 메뉴

  @Nested
  @DisplayName("메뉴 관리")
  class MenuAdminTest {

    private static final String MENUS = "/api/v1/menus";

    private static String menuBody(Long parent, String type, String url, String permCd,
        String i18n) {
      return """
          {"parentMenuId":%s,"viewTypeCd":"%s","menuUrl":%s,"permCd":%s,"sortOrder":5,
           "newWindowYn":"N","adminMenuYn":"N","sideMenuYn":"N","displayYn":"Y","useYn":"Y",
           "i18n":%s}""".formatted(parent, type, url == null ? "null" : "\"" + url + "\"",
          permCd == null ? "null" : "\"" + permCd + "\"", i18n);
    }

    @Test
    @DisplayName("트리 - 미노출(DISPLAY_YN=N) 메뉴 포함, 상세에 언어별 이름·본문")
    void treeAndDetail() throws Exception {
      admin(get(MENUS))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].children[0].children[0].children[0].menuId").value(4))
          .andExpect(jsonPath("$.result[0].children[0].children[0].children[0].displayYn")
              .value("N"));
      admin(get(MENUS + "/6"))
          .andExpect(jsonPath("$.result.viewTypeCd").value("CONTENT"))
          .andExpect(jsonPath("$.result.i18n[?(@.langCd == 'en')].menuContent")
              .value(hasItem(containsString("TX Guide"))));
    }

    @Test
    @DisplayName("등록 - CONTENT 본문은 정화(script 제거)되어 저장")
    void createContentMenu_sanitized() throws Exception {
      long id = idOf(admin(json(post(MENUS), menuBody(11L, "CONTENT", null, null, """
          [{"langCd":"ko","menuNm":"이용 규칙","menuContent":"<p>규칙</p><script>alert(1)</script>"}]
          """))).andExpect(status().isCreated()), "$.result.menuId");

      admin(get(MENUS + "/" + id))
          .andExpect(jsonPath("$.result.menuUrl").doesNotExist())
          .andExpect(jsonPath("$.result.i18n[0].menuContent").value("<p>규칙</p>"));
    }

    @Test
    @DisplayName("URL 검증 - MENU 는 / 경로, LINK 는 http(s), CONTENT 는 없음, 위반 시 B5004")
    void urlValidation() throws Exception {
      String names = "[{\"langCd\":\"ko\",\"menuNm\":\"x\"}]";
      admin(json(post(MENUS), menuBody(null, "MENU", "javascript:alert(1)", null, names)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B5004"));
      admin(json(post(MENUS), menuBody(null, "LINK", "/relative", null, names)))
          .andExpect(jsonPath("$.messageId").value("B5004"));
      admin(json(post(MENUS), menuBody(null, "MENU", "//evil.example", null, names)))
          .andExpect(jsonPath("$.messageId").value("B5004"));
      admin(json(post(MENUS), menuBody(null, "CONTENT", "/x", null, names)))
          .andExpect(jsonPath("$.messageId").value("B5004"));
      admin(json(post(MENUS), menuBody(null, "UNKNOWN", null, null, names)))
          .andExpect(jsonPath("$.messageId").value("B5005"));
    }

    @Test
    @DisplayName("상위 메뉴 순환 방지 B5003, 하위 메뉴가 있으면 삭제 불가 B5002")
    void treeIntegrity() throws Exception {
      String names = "[{\"langCd\":\"ko\",\"menuNm\":\"TX\"}]";
      admin(json(put(MENUS + "/1"), menuBody(3L, "MENU", "/tx", null, names)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B5003"));
      admin(delete(MENUS + "/1"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B5002"));
    }

    @Test
    @DisplayName("순서 변경 - 같은 상위 메뉴만 (다르면 B5006)")
    void reorder() throws Exception {
      admin(json(put(MENUS + "/order"), "{\"parentMenuId\":11,\"menuIds\":[14,13,12]}"))
          .andExpect(status().isOk());
      admin(get(MENUS))
          .andExpect(jsonPath("$.result[?(@.menuId == 11)].children[0].menuId").value(hasItem(14)));
      admin(json(put(MENUS + "/order"), "{\"parentMenuId\":11,\"menuIds\":[12,3]}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.messageId").value("B5006"));
    }

    @Test
    @DisplayName("권한코드 변경은 즉시 API 권한에 반영 (권한 캐시 삭제) - 거래목록 permCd 변경 시 조회 권한 상실")
    void permCdChange_evictsAuthorityCache() throws Exception {
      as("tx.viewer", "sample.list", get("/api/v1/samples")).andExpect(status().isOk());

      admin(json(put(MENUS + "/3"), menuBody(2L, "MENU", "/tx/trades/list", "sample-v2", """
          [{"langCd":"ko","menuNm":"거래목록"},{"langCd":"en","menuNm":"Trade List"}]""")))
          .andExpect(status().isOk());

      as("tx.viewer", "sample.list", get("/api/v1/samples")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("본문 메뉴 내용 - 내 메뉴에 있으면 조회(언어 대체), 없으면 B5007")
    void content() throws Exception {
      as("emp01", "openid", get(MENUS + "/6/content")
          .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.menuNm").value("TX 이용안내"))
          .andExpect(jsonPath("$.result.fallback").value(false))
          .andExpect(jsonPath("$.result.contentHtml").value(containsString("TX 모듈")));
      as("partner01", "openid", get(MENUS + "/6/content"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B5007"));
      as("emp01", "openid", get(MENUS + "/3/content"))
          .andExpect(jsonPath("$.messageId").value("B5007"));
    }
  }

  // ==================================================================== 권한

  @Nested
  @DisplayName("권한 관리")
  class RoleAdminTest {

    private static final String ROLES = "/api/v1/roles";

    private static String roleBody(String roleCd, String type, int level) {
      return """
          {"roleCd":"%s","names":{"ko":"감사","en":"Auditor"},"roleTypeCd":"%s",
           "roleLevel":%d,"moduleCd":"TX","sortOrder":30,"useYn":"Y"}"""
          .formatted(roleCd, type, level);
    }

    @Test
    @DisplayName("권한 목록 - 언어별 이름, 부여·메뉴 수")
    void roles() throws Exception {
      admin(get(ROLES))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].roleCd").value("SYS_ADMIN"))
          .andExpect(jsonPath("$.result[?(@.roleCd == 'USER_EMP')].deptGrantCnt").value(hasItem(4)));
    }

    @Test
    @DisplayName("등록·수정·삭제 - 코드 중복 B6002, 기본 권한 삭제 B6003, 부여 내역 있으면 삭제 불가 B6005")
    void roleCrud() throws Exception {
      long id = idOf(admin(json(post(ROLES), roleBody("TX_AUDITOR", "MODULE_MANAGER", 3)))
          .andExpect(status().isCreated()), "$.result.roleId");
      admin(json(post(ROLES), roleBody("TX_AUDITOR", "MODULE_MANAGER", 3)))
          .andExpect(jsonPath("$.messageId").value("B6002"));
      admin(json(put(ROLES + "/" + id), roleBody("IGNORED", "GENERAL_USER", 4)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.roleCd").value("TX_AUDITOR"))
          .andExpect(jsonPath("$.result.roleLevel").value(4));

      admin(delete(ROLES + "/1")).andExpect(jsonPath("$.messageId").value("B6003"));
      admin(json(put(ROLES + "/4"), roleBody("TX_MANAGER", "GENERAL_USER", 4)))
          .andExpect(jsonPath("$.messageId").value("B6003"));
      admin(delete(ROLES + "/6")).andExpect(jsonPath("$.messageId").value("B6005"));

      admin(json(post(ROLES + "/" + id + "/grants"), """
          {"targetType":"USER","targetId":"emp01","startDt":"2026-01-01"}"""))
          .andExpect(status().isCreated());
      admin(delete(ROLES + "/" + id))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B6005"));
    }

    @Test
    @DisplayName("메뉴 접근 저장 → 즉시 메뉴·API 권한 반영 (상위 메뉴 조회 자동, 등록 체크 시 조회 포함)")
    void roleMenus_appliedImmediately() throws Exception {
      as("emp01", "code.list code.add", get("/api/v1/code-groups"))
          .andExpect(status().isForbidden());

      admin(json(put(ROLES + "/7/menus"), """
          {"items":[{"menuId":12,"listYn":"N","addYn":"Y","editYn":"N","delYn":"N"},
                    {"menuId":15,"listYn":"Y","addYn":"N","editYn":"N","delYn":"N"}]}"""))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.menus[?(@.menuId == 11)].listYn").value(hasItem("Y")))
          .andExpect(jsonPath("$.result.menus[?(@.menuId == 1)].listYn").value(hasItem("N")));

      as("emp01", "code.list code.add", get("/api/v1/code-groups")).andExpect(status().isOk());
      as("emp01", "openid", get("/api/v1/menus/me"))
          .andExpect(jsonPath("$.result[*].menuId").value(hasItems(11, 15)))
          .andExpect(jsonPath("$.result[*].menuId").value(not(hasItem(1))));
    }

    @Test
    @DisplayName("부여 - 사용자·부서, 기간 중복 B6010, 대상 없음 B6007, 기간 변경, 회수 시 권한 즉시 상실")
    void grants() throws Exception {
      admin(json(put(ROLES + "/2/menus"), """
          {"items":[{"menuId":14,"listYn":"Y","addYn":"N","editYn":"N","delYn":"N"}]}"""))
          .andExpect(status().isOk());
      as("emp01", "role.list", get(ROLES)).andExpect(status().isForbidden());

      long grantId = idOf(admin(json(post(ROLES + "/2/grants"), """
          {"targetType":"USER","targetId":"emp01","startDt":"2026-01-01","grantReason":"대행"}"""))
          .andExpect(status().isCreated()), "$.result.users[?(@.targetId == 'emp01')].grantId");
      as("emp01", "role.list", get(ROLES)).andExpect(status().isOk());

      admin(json(post(ROLES + "/2/grants"), """
          {"targetType":"USER","targetId":"emp01","startDt":"2026-06-01","endDt":"2026-06-30"}"""))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.messageId").value("B6010"));
      admin(json(post(ROLES + "/2/grants"), """
          {"targetType":"DEPT","targetId":"NO_DEPT","startDt":"2026-01-01"}"""))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.messageId").value("B6007"));
      admin(json(post(ROLES + "/2/grants"), """
          {"targetType":"USER","targetId":"emp01","startDt":"2026-12-31","endDt":"2026-01-01"}"""))
          .andExpect(jsonPath("$.messageId").value("B6009"));

      admin(json(put(ROLES + "/2/grants/USER/" + grantId), """
          {"targetType":"USER","targetId":"emp01","startDt":"2099-01-01"}"""))
          .andExpect(jsonPath("$.result.users[?(@.targetId == 'emp01')].status")
              .value(hasItem("SCHEDULED")));
      as("emp01", "role.list", get(ROLES)).andExpect(status().isForbidden());

      admin(delete(ROLES + "/2/grants/USER/" + grantId)).andExpect(status().isOk());
      admin(get(ROLES + "/2/grants"))
          .andExpect(jsonPath("$.result.users[*].targetId").value(not(hasItem("emp01"))))
          .andExpect(jsonPath("$.result.users[?(@.targetId == 'tx.master')].targetSubNm")
              .value(hasItem("TX팀")));
    }

    @Test
    @DisplayName("위임 관리자(role.* 를 메뉴 권한으로 받은 TX 마스터)도 시스템관리자 권한은 변경·부여 불가 B6004")
    void delegatedAdmin_cannotTouchSysAdmin() throws Exception {
      admin(json(put(ROLES + "/2/menus"), """
          {"items":[{"menuId":14,"listYn":"Y","addYn":"Y","editYn":"Y","delYn":"N"}]}"""))
          .andExpect(status().isOk());
      String scope = "role.list role.add role.edit";

      as("tx.master", scope, get(ROLES)).andExpect(status().isOk());
      as("tx.master", scope, json(post(ROLES + "/1/grants"), """
          {"targetType":"USER","targetId":"tx.master","startDt":"2026-01-01"}"""))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messageId").value("B6004"));
      as("tx.master", scope, json(post(ROLES), roleBody("SUPER", "SYSTEM_ADMIN", 1)))
          .andExpect(jsonPath("$.messageId").value("B6004"));
      as("tx.master", scope, json(put(ROLES + "/1/menus"), "{\"items\":[]}"))
          .andExpect(jsonPath("$.messageId").value("B6004"));
      as("tx.master", scope, delete(ROLES + "/7")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("사용자 검색 - ID·이름 부분 일치, 주부서명, 와일드카드 문자 그대로")
    void searchUsers() throws Exception {
      admin(get("/api/v1/users").param("keyword", "EMP"))
          .andExpect(jsonPath("$.result[*].userId").value(hasItem("emp01")))
          .andExpect(jsonPath("$.result[?(@.userId == 'emp01')].deptNm").value(hasItem("WS팀")));
      admin(get("/api/v1/users").param("keyword", "%"))
          .andExpect(jsonPath("$.result.length()").value(0));
      as("tx.viewer", "role.list", get("/api/v1/users").param("keyword", "emp"))
          .andExpect(status().isForbidden());
    }
  }
}
