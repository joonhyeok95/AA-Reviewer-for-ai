package com.flowlog.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowlog.backend.global.security.ScopeActions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 게시판 scope (board.list / write / update / delete) - IdP scope ∩ DB 업무 권한.
 *
 * <p>DB 권한(TB_ROLE_MENU)의 작업은 LIST/ADD/EDIT/DEL 이고 scope 동사 write/update/delete 는 각각
 * add/edit/del 로 대응된다. ({@link ScopeActions}) 권한 이름은 IdP scope 그대로 (SCOPE_board.write).
 *
 * @author flowlog
 * @since 2026-11-06
 */
@SpringBootTest
@Transactional
class BoardScopeIntegrationTest {

  private static final String ME_URL = "/api/v1/auth/me";
  private static final String ALL_BOARD = "openid board.list board.write board.update board.delete";

  private static final Map<String, String> SUBJECTS = Map.of(
      "admin", "00000000-0000-4000-8000-000000000001",
      "emp01", "00000000-0000-4000-8000-000000000005",
      "board.manager", "00000000-0000-4000-8000-000000000008",
      "board.writer", "00000000-0000-4000-8000-000000000009",
      "board.reader", "00000000-0000-4000-8000-000000000010",
      "board.limited", "00000000-0000-4000-8000-000000000011");

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
        .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  private String token(String username, String scope) {
    String value = "board-" + username + "-" + scope.hashCode();
    Jwt jwt = Jwt.withTokenValue(value).header("alg", "RS256")
        .subject(SUBJECTS.get(username))
        .issuer("http://localhost:7000/realms/flowlog").audience(List.of("flowlog-api"))
        .claims(c -> c.putAll(Map.of("preferred_username", username, "azp", "flowlog-bff",
            "scope", scope)))
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    given(jwtDecoder.decode(value)).willReturn(jwt);
    return "Bearer " + value;
  }

  private List<String> boardScopes(String username, String scope) throws Exception {
    String body = mockMvc.perform(get(ME_URL)
            .header(HttpHeaders.AUTHORIZATION, token(username, scope)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    List<String> authorities = com.jayway.jsonpath.JsonPath.read(body, "$.result.authorities");
    return authorities.stream().filter(a -> a.startsWith("SCOPE_board.")).sorted().toList();
  }

  @Test
  @DisplayName("작업 동의어 - write=add, update=edit, delete=del, 형식 외 scope 는 그대로")
  void scopeActions() {
    assertThat(ScopeActions.toPermission("board.write")).isEqualTo("board.add");
    assertThat(ScopeActions.toPermission("board.update")).isEqualTo("board.edit");
    assertThat(ScopeActions.toPermission("board.delete")).isEqualTo("board.del");
    assertThat(ScopeActions.toPermission("board.list")).isEqualTo("board.list");
    assertThat(ScopeActions.toPermission("openid")).isEqualTo("openid");
    assertThat(ScopeActions.isApiScope("board.write")).isTrue();
    assertThat(ScopeActions.isApiScope("default-roles-flowlog")).isFalse();
  }

  @Test
  @DisplayName("board.manager - DB 전체 → 4개 scope 모두")
  void manager() throws Exception {
    assertThat(boardScopes("board.manager", ALL_BOARD)).containsExactly(
        "SCOPE_board.delete", "SCOPE_board.list", "SCOPE_board.update", "SCOPE_board.write");
  }

  @Test
  @DisplayName("board.writer - DB 삭제 없음 → 토큰에 delete 가 있어도 list/write/update")
  void writer() throws Exception {
    assertThat(boardScopes("board.writer", ALL_BOARD)).containsExactly(
        "SCOPE_board.list", "SCOPE_board.update", "SCOPE_board.write");
  }

  @Test
  @DisplayName("board.reader / board.limited - DB 조회만 → list 만 (Keycloak 에 4개가 있어도)")
  void readerAndLimited() throws Exception {
    assertThat(boardScopes("board.reader", "openid board.list")).containsExactly("SCOPE_board.list");
    assertThat(boardScopes("board.limited", ALL_BOARD)).containsExactly("SCOPE_board.list");
  }

  @Test
  @DisplayName("DB 게시판 권한 없는 사용자(emp01)는 scope 가 있어도 0개, 토큰에 없는 scope 는 DB 가 있어도 안 줌")
  void intersection() throws Exception {
    assertThat(boardScopes("emp01", ALL_BOARD)).isEmpty();
    assertThat(boardScopes("board.manager", "openid board.list")).containsExactly(
        "SCOPE_board.list");
  }

  @Test
  @DisplayName("내 메뉴 - 게시판(/support/boards) 메뉴 권한 표시 (writer: 삭제 false)")
  void boardMenu() throws Exception {
    mockMvc.perform(get("/api/v1/menus/me")
            .header(HttpHeaders.AUTHORIZATION, token("board.writer", "openid")))
        .andExpect(jsonPath("$.result[?(@.menuUrl == '/support/boards')].permission.add")
            .value(hasItem(true)))
        .andExpect(jsonPath("$.result[?(@.menuUrl == '/support/boards')].permission.del")
            .value(hasItem(false)));
    mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token("board.writer", "openid")))
        .andExpect(jsonPath("$.result.roles[*].roleCd").value(containsInAnyOrder("BOARD_WRITER",
            "USER_EMP")));
  }
}
