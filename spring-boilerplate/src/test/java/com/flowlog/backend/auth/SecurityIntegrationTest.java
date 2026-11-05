package com.flowlog.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowlog.backend.global.security.SamlRoleAuthoritiesConverter;
import com.flowlog.backend.global.security.SessionTokenAuthorizationFilter;
import com.flowlog.backend.global.web.TrustedForwardedHeaderFilter;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 인증/인가 통합 테스트. (OAuth2 Bearer / BFF 세션 / SAML2 세션)
 *
 * <ul>
 *   <li>사용자 매핑은 IdP 고유ID(sub) 기준. 사용자명(preferred_username, NameID)은 위조해도 권한에 영향 없음.</li>
 *   <li>API 권한 = IdP scope ∩ DB 업무 권한(TB_ROLE_MENU + TB_MENU.PERM_CD).</li>
 *   <li>OAuth2 : JwtDecoder 만 Mock (Keycloak 서명 검증 대체). 변환기 → DB 권한 조회는 실제 동작.</li>
 *   <li>SAML2 : 로그인 완료 후 상태(Saml2AssertionAuthentication)를 주입. 권한은 요청마다 재계산된다.
 *       Keycloak 과의 실제 왕복은 infra/keycloak/e2e 스크립트로 검증한다.</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest
@Transactional
class SecurityIntegrationTest {

  private static final String ME_URL = "/api/v1/auth/me";
  private static final String MENU_URL = "/api/v1/menus/me";
  private static final String SAMPLE_URL = "/api/v1/samples";

  /** Keycloak user id (realm import 와 TB_USER.IDP_SUBJECT 가 같은 값). */
  private static final Map<String, String> SUBJECTS = Map.of(
      "admin", "00000000-0000-4000-8000-000000000001",
      "tx.master", "00000000-0000-4000-8000-000000000002",
      "tx.manager", "00000000-0000-4000-8000-000000000003",
      "tx.viewer", "00000000-0000-4000-8000-000000000004",
      "emp01", "00000000-0000-4000-8000-000000000005",
      "partner01", "00000000-0000-4000-8000-000000000006",
      "guest01", "00000000-0000-4000-8000-000000000007");

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private SamlRoleAuthoritiesConverter samlRoleAuthoritiesConverter;

  @Autowired
  private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired
  private OAuth2AuthorizedClientRepository authorizedClientRepository;

  @Autowired
  private FilterRegistrationBean<TrustedForwardedHeaderFilter> forwardedHeaderFilter;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(forwardedHeaderFilter.getFilter())
        .apply(springSecurity())
        .build();
  }

  private static String sub(String username) {
    return SUBJECTS.getOrDefault(username, "unknown-" + username);
  }

  /** Keycloak 형식의 Access Token 을 등록하고 Bearer 헤더 값을 반환한다. */
  private String bearer(
      String tokenValue, String subject, String username, String clientId, String scope) {
    Jwt jwt = Jwt.withTokenValue(tokenValue)
        .header("alg", "RS256")
        .subject(subject)
        .issuer("http://localhost:7000/realms/flowlog")
        .audience(List.of("flowlog-api"))
        .claims(claims -> claims.putAll(Map.of(
            "preferred_username", username, "azp", clientId, "scope", scope)))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build();
    given(jwtDecoder.decode(tokenValue)).willReturn(jwt);
    return "Bearer " + tokenValue;
  }

  private String bearer(String tokenValue, String username, String clientId, String scope) {
    return bearer(tokenValue, sub(username), username, clientId, scope);
  }

  @Nested
  @DisplayName("미인증 / 공개 경로 / 기본 거부")
  class AnonymousTest {

    @Test
    @DisplayName("인증 없이 API 호출 - 401, C0009 표준 응답, WWW-Authenticate 헤더")
    void unauthenticated() throws Exception {
      mockMvc.perform(get(ME_URL))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
          .andExpect(jsonPath("$.httpStatus").value(401))
          .andExpect(jsonPath("$.messageId").value("C0009"))
          .andExpect(jsonPath("$.messageTitle").value("인증 필요"))
          .andExpect(jsonPath("$.path").value(ME_URL));
    }

    @Test
    @DisplayName("401 응답도 다국어 적용 - Accept-Language: en-US")
    void unauthenticated_english() throws Exception {
      mockMvc.perform(get(ME_URL).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.locale").value("en_US"))
          .andExpect(jsonPath("$.messageTitle").value("Unauthorized"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰 - 401, C0009")
    void invalidToken() throws Exception {
      when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("invalid signature"));

      mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer broken"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messageId").value("C0009"));
    }

    @Test
    @DisplayName("공개 경로 - API 문서는 인증 없이 접근")
    void publicPath() throws Exception {
      mockMvc.perform(get("/v3/api-docs"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
          .andExpect(jsonPath("$.components.securitySchemes.keycloak").doesNotExist());
    }

    @Test
    @DisplayName("인가 규칙이 없는 경로는 시스템관리자도 403 (기본 거부)")
    void unmappedPath_deniedByDefault() throws Exception {
      String token = bearer("admin-unmapped", "admin", "flowlog-bff", "openid");

      mockMvc.perform(get("/api/v1/not-registered").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messageId").value("C0010"));
    }
  }

  @Nested
  @DisplayName("OAuth2 (Bearer JWT)")
  class OAuth2Test {

    @Test
    @DisplayName("사용자 토큰 - sub 로 DB 권한 매핑, API 권한 = 토큰 scope ∩ DB 권한 (조회 전용 권한이면 add 제외)")
    void userToken_scopeIntersectsDbPermission() throws Exception {
      String token = bearer("viewer-token", "tx.viewer", "flowlog-bff",
          "openid sample.list sample.add");

      mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.authType").value("OAUTH2_BEARER"))
          .andExpect(jsonPath("$.result.userId").value("tx.viewer"))
          .andExpect(jsonPath("$.result.userNm").value("TX조회매니저"))
          .andExpect(jsonPath("$.result.registered").value(true))
          .andExpect(jsonPath("$.result.clientId").value("flowlog-bff"))
          .andExpect(jsonPath("$.result.roles[*].roleCd").value(
              hasItems("TX_MANAGER_READ", "USER_EMP")))
          .andExpect(jsonPath("$.result.authorities").value(hasItems(
              "ROLE_TX_MANAGER_READ", "ROLE_USER_EMP", "SCOPE_sample.list")))
          .andExpect(jsonPath("$.result.authorities").value(not(hasItems("SCOPE_sample.add"))));

      mockMvc.perform(post(SAMPLE_URL).header(HttpHeaders.AUTHORIZATION, token)
              .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"useYn\":\"Y\"}"))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("사용자명 위조 - preferred_username=admin 이어도 sub 가 미등록이면 관리자 권한 없음")
    void usernameSpoofing_noAdmin() throws Exception {
      String token = bearer("spoof-token", sub("guest01"), "admin", "flowlog-bff",
          "openid sample.del");

      mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(jsonPath("$.result.registered").value(false))
          .andExpect(jsonPath("$.result.authorities").isEmpty());
      mockMvc.perform(delete(SAMPLE_URL + "/3").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TB_USER 미등록 사용자 - 인증은 되지만 IdP scope 가 있어도 API 권한 없음")
    void unregisteredUser() throws Exception {
      String token = bearer("guest-token", "guest01", "flowlog-bff", "openid sample.list");

      mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.registered").value(false))
          .andExpect(jsonPath("$.result.roles").isEmpty());
      mockMvc.perform(get(SAMPLE_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Client Credentials 토큰 - 허용된 client(azp)의 서비스 계정, scope 로만 인가 (삭제 허용)")
    void clientCredentials_scopeAuthorized() throws Exception {
      String token = bearer("batch-token", "service-account-flowlog-batch", "flowlog-batch",
          "sample.list sample.del");

      mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(jsonPath("$.result.serviceAccount").value(true))
          .andExpect(jsonPath("$.result.roles").isEmpty())
          .andExpect(jsonPath("$.result.authorities").value(hasItems("SCOPE_sample.del")));

      mockMvc.perform(delete(SAMPLE_URL + "/3").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messageId").value("S0005"));
    }

    @Test
    @DisplayName("서비스 계정 위조 - 사용자명만 service-account-* 이고 azp 가 다르면 사용자로 취급 (권한 없음)")
    void fakeServiceAccount_denied() throws Exception {
      String token = bearer("fake-batch-token", "unknown-sa", "service-account-flowlog-batch",
          "flowlog-bff", "sample.list sample.del");

      mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(jsonPath("$.result.serviceAccount").value(false))
          .andExpect(jsonPath("$.result.authorities").isEmpty());
      mockMvc.perform(delete(SAMPLE_URL + "/3").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인가 실패 - sample.del scope 도 SYS_ADMIN 권한도 없으면 삭제 403, C0010")
    void forbidden() throws Exception {
      String batchToken = bearer("batch-list-token", "service-account-flowlog-batch",
          "flowlog-batch", "sample.list");
      String viewerToken = bearer("viewer-token2", "tx.viewer", "flowlog-bff", "openid");

      mockMvc.perform(delete(SAMPLE_URL + "/3").header(HttpHeaders.AUTHORIZATION, batchToken))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messageId").value("C0010"));
      mockMvc.perform(delete(SAMPLE_URL + "/3").header(HttpHeaders.AUTHORIZATION, viewerToken))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("시스템관리자 사용자 토큰 - DB 권한(ROLE_SYS_ADMIN)으로 삭제 허용")
    void sysAdmin_deleteAllowed() throws Exception {
      String token = bearer("admin-token", "admin", "flowlog-bff", "openid");

      mockMvc.perform(delete(SAMPLE_URL + "/3").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bearer 요청은 CSRF 검사 제외 + sample.add(scope∩DB) 로 등록 + 생성자 = 토큰 사용자")
    void bearer_noCsrf_auditing() throws Exception {
      String token = bearer("manager-token", "tx.manager", "flowlog-bff", "sample.add");

      mockMvc.perform(post(SAMPLE_URL).header(HttpHeaders.AUTHORIZATION, token)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"OAuth2 등록\",\"useYn\":\"Y\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.createdBy").value("tx.manager"));
    }
  }

  @Nested
  @DisplayName("OAuth2 BFF (세션 - 토큰은 서버 세션에만)")
  class OAuth2SessionTest {

    private static final String TOKEN_VALUE = "server-side-only-access-token-value";

    private OAuth2AuthenticationToken oauth2Authentication;

    /**
     * 실제 로그인 완료 후와 같은 세션을 만든다.
     * (OAuth2AuthorizedClient 를 HttpSession 저장소에 저장 - 운영에서는 이 세션이 Redis 에 저장됨)
     */
    private MockHttpSession loginSession(String username, Set<String> tokenScopes,
        Instant expiresAt, List<String> loginAuthorities) {
      ClientRegistration registration =
          clientRegistrationRepository.findByRegistrationId("keycloak");
      OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
          .subject(sub(username))
          .claim("preferred_username", username)
          .claim("azp", "flowlog-bff")
          .issuedAt(Instant.now().minusSeconds(10))
          .expiresAt(Instant.now().plusSeconds(300))
          .build();
      DefaultOidcUser user = new DefaultOidcUser(
          List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken, "preferred_username");
      oauth2Authentication = new OAuth2AuthenticationToken(user,
          loginAuthorities.stream().map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
              .toList(), "keycloak");
      OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
          TOKEN_VALUE, expiresAt.minusSeconds(60), expiresAt, tokenScopes);
      OAuth2AuthorizedClient client =
          new OAuth2AuthorizedClient(registration, username, accessToken);

      MockHttpSession session = new MockHttpSession();
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setSession(session);
      authorizedClientRepository.saveAuthorizedClient(client, oauth2Authentication, request,
          new MockHttpServletResponse());
      return session;
    }

    @Test
    @DisplayName("세션 로그인 - authType OAUTH2_SESSION, 권한 = 세션 토큰 scope ∩ DB + DB ROLE, 응답에 토큰 없음")
    void sessionLogin_me() throws Exception {
      MockHttpSession session = loginSession("tx.viewer", Set.of("openid", "sample.list"),
          Instant.now().plusSeconds(300), List.of("OIDC_USER"));

      String body = mockMvc.perform(get(ME_URL).session(session)
              .with(authentication(oauth2Authentication)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.authType").value("OAUTH2_SESSION"))
          .andExpect(jsonPath("$.result.userId").value("tx.viewer"))
          .andExpect(jsonPath("$.result.clientId").value("flowlog-bff"))
          .andExpect(jsonPath("$.result.authorities").value(hasItems(
              "SCOPE_sample.list", "ROLE_TX_MANAGER_READ", "ROLE_USER_EMP")))
          .andReturn().getResponse().getContentAsString();

      assertThat(body).doesNotContain(TOKEN_VALUE).doesNotContain("id-token");
    }

    @Test
    @DisplayName("인가는 로그인 시점 권한이 아닌 세션 토큰의 scope 기준 - 토큰에 sample.add 없으면 등록 403")
    void authorizesByTokenScope() throws Exception {
      MockHttpSession session = loginSession("tx.viewer", Set.of("sample.list"),
          Instant.now().plusSeconds(300), List.of("SCOPE_sample.add")); // 로그인 시점 권한은 무시됨

      mockMvc.perform(get(SAMPLE_URL).session(session)
              .with(authentication(oauth2Authentication)))
          .andExpect(status().isOk());
      mockMvc.perform(post(SAMPLE_URL).session(session).with(csrf())
              .with(authentication(oauth2Authentication))
              .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"useYn\":\"Y\"}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messageId").value("C0010"));
    }

    @Test
    @DisplayName("커스텀 권한(조회 전용) 반영 - IdP 가 sample.add 를 줘도 DB 권한이 조회뿐이면 등록 403")
    void customReadOnlyRole_limitsScope() throws Exception {
      MockHttpSession session = loginSession("tx.viewer", Set.of("sample.list", "sample.add"),
          Instant.now().plusSeconds(300), List.of("OIDC_USER"));

      mockMvc.perform(post(SAMPLE_URL).session(session).with(csrf())
              .with(authentication(oauth2Authentication))
              .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"useYn\":\"Y\"}"))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("세션 토큰 scope sample.add + DB 권한 add 가 있으면 등록 201, 생성자 = 세션 사용자")
    void sessionWithAddScope_create() throws Exception {
      MockHttpSession session = loginSession("tx.manager", Set.of("sample.list", "sample.add"),
          Instant.now().plusSeconds(300), List.of("OIDC_USER"));

      mockMvc.perform(post(SAMPLE_URL).session(session).with(csrf())
              .with(authentication(oauth2Authentication))
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"name\":\"BFF 등록\",\"useYn\":\"Y\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.createdBy").value("tx.manager"));
    }

    @Test
    @DisplayName("세션 토큰 만료 + refresh token 없음 - 세션 폐기 후 401 C0009")
    void expiredToken_invalidatesSession() throws Exception {
      MockHttpSession session = loginSession("tx.viewer", Set.of("sample.list"),
          Instant.now().minusSeconds(5), List.of("OIDC_USER"));

      mockMvc.perform(get(ME_URL).session(session).with(authentication(oauth2Authentication)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messageId").value("C0009"));
      assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("세션에 토큰이 없으면 (로그인 상태 위조/유실) 401 C0009")
    void noTokenInSession() throws Exception {
      loginSession("tx.viewer", Set.of("sample.list"), Instant.now().plusSeconds(300),
          List.of("OIDC_USER"));

      mockMvc.perform(get(ME_URL).session(new MockHttpSession())
              .with(authentication(oauth2Authentication)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("SAML2 (세션)")
  class Saml2Test {

    /** 실제 로그인 결과와 같은 SAML 인증 (sub/Role 속성). 로그인 시점 권한은 요청마다 재계산된다. */
    private Saml2AssertionAuthentication samlSession(
        String nameId, String subject, List<String> loginAuthorities, String... roles) {
      Saml2ResponseAssertion assertion = Saml2ResponseAssertion
          .withResponseValue("<saml2p:Response/>")
          .nameId(nameId)
          .attributes(Map.of("sub", List.of(subject), "Role", List.copyOf(Arrays.asList(roles))))
          .build();
      return new Saml2AssertionAuthentication(nameId, assertion,
          loginAuthorities.stream().map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
              .toList(), "keycloak");
    }

    private Saml2AssertionAuthentication samlSession(String username, String... roles) {
      return samlSession(username, sub(username), List.of(), roles);
    }

    @Test
    @DisplayName("SAML 로그인 세션 - authType SAML2, sub 로 사용자 매핑, Role ∩ DB 권한")
    void samlSession_me() throws Exception {
      mockMvc.perform(get(ME_URL).with(authentication(
              samlSession("tx.manager", "sample.list", "sample.add"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.authType").value("SAML2"))
          .andExpect(jsonPath("$.result.userId").value("tx.manager"))
          .andExpect(jsonPath("$.result.clientId").doesNotExist())
          .andExpect(jsonPath("$.result.roles[0].roleCd").value("TX_MANAGER"))
          .andExpect(jsonPath("$.result.authorities").value(hasItems(
              "SCOPE_sample.add", "SCOPE_sample.list", "ROLE_TX_MANAGER")));
    }

    @Test
    @DisplayName("세션 요청의 변경 API 는 CSRF 토큰 필수 - 없으면 403, 있으면 201")
    void samlSession_csrf() throws Exception {
      String body = "{\"name\":\"SAML 등록\",\"useYn\":\"Y\"}";

      mockMvc.perform(post(SAMPLE_URL)
              .with(authentication(samlSession("tx.manager", "sample.list", "sample.add")))
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messageId").value("C0010"));

      mockMvc.perform(post(SAMPLE_URL)
              .with(authentication(samlSession("tx.manager", "sample.list", "sample.add")))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.result.createdBy").value("tx.manager"));
    }

    @Test
    @DisplayName("세션에 저장된 로그인 시점 권한은 무시 - 요청마다 DB 권한으로 재계산 (ROLE_SYS_ADMIN 위조 불가)")
    void storedAuthoritiesIgnored() throws Exception {
      Saml2AssertionAuthentication session = samlSession("emp01", sub("emp01"),
          List.of("ROLE_SYS_ADMIN", "SCOPE_sample.del"), "sample.list");

      mockMvc.perform(delete(SAMPLE_URL + "/3").with(authentication(session)).with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.messageId").value("C0010"));
    }

    @Test
    @DisplayName("NameID 위조 - NameID=admin 이어도 sub 가 emp01 이면 관리자 권한 없음")
    void nameIdSpoofing_noAdmin() throws Exception {
      Saml2AssertionAuthentication session =
          samlSession("admin", sub("emp01"), List.of(), "sample.list");

      mockMvc.perform(get(ME_URL).with(authentication(session)))
          .andExpect(jsonPath("$.result.userId").value("emp01"))
          .andExpect(jsonPath("$.result.authorities").value(not(hasItems("ROLE_SYS_ADMIN"))));
    }

    @Test
    @DisplayName("SAML 로그인 유효시간(max-auth-age) 경과 - 세션 폐기 후 401 (재로그인 유도)")
    void samlAuthExpired() throws Exception {
      MockHttpSession session = new MockHttpSession();
      session.setAttribute(SessionTokenAuthorizationFilter.AUTH_TIME_ATTRIBUTE,
          Instant.now().minusSeconds(7200).toEpochMilli());

      mockMvc.perform(get(ME_URL).session(session)
              .with(authentication(samlSession("tx.manager", "sample.list"))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.messageId").value("C0009"));
      assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("SAML Assertion sub/Role → 권한 (tx.viewer: 부서 부여 조회 전용 권한이라 add 제외)")
    void assertionToAuthorities() {
      Assertion assertion = assertion(Map.of(
          "sub", List.of(sub("tx.viewer")),
          "Role", List.of("sample.list", "sample.add", "offline_access",
              "default-roles-flowlog")));

      Collection<GrantedAuthority> authorities = samlRoleAuthoritiesConverter.convert(assertion);

      assertThat(authorities).extracting(GrantedAuthority::getAuthority)
          .containsExactly("SCOPE_sample.list", "ROLE_TX_MANAGER_READ", "ROLE_USER_EMP");
    }

    @Test
    @DisplayName("SAML Assertion 에 sub 가 없으면 권한 없음")
    void assertionWithoutSubject() {
      Assertion assertion = assertion(Map.of("Role", List.of("sample.list")));

      assertThat(samlRoleAuthoritiesConverter.convert(assertion)).isEmpty();
    }

    private Assertion assertion(Map<String, List<String>> attributes) {
      List<Attribute> mocked = attributes.entrySet().stream().map(entry -> {
        Attribute attribute = mock(Attribute.class);
        when(attribute.getName()).thenReturn(entry.getKey());
        List<XMLObject> values = entry.getValue().stream()
            .map(value -> (XMLObject) xsString(value)).toList();
        when(attribute.getAttributeValues()).thenReturn(values);
        return attribute;
      }).toList();
      AttributeStatement statement = mock(AttributeStatement.class);
      when(statement.getAttributes()).thenReturn(mocked);
      Assertion assertion = mock(Assertion.class);
      when(assertion.getAttributeStatements()).thenReturn(List.of(statement));
      return assertion;
    }

    private XSString xsString(String value) {
      XSString xs = mock(XSString.class);
      when(xs.getValue()).thenReturn(value);
      return xs;
    }
  }

  @Nested
  @DisplayName("메뉴 API (GET /api/v1/menus/me)")
  class MenuTest {

    @Test
    @DisplayName("tx.viewer - 유효 권한(부서 부여)의 메뉴만, 시스템관리 메뉴 없음, 거래목록은 조회만")
    void viewerMenus() throws Exception {
      String token = bearer("menu-viewer", "tx.viewer", "flowlog-bff", "openid");

      mockMvc.perform(get(MENU_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[*].menuId").value(hasItems(1, 7, 15, 16)))
          .andExpect(jsonPath("$.result[*].menuId").value(not(hasItems(11))))
          .andExpect(jsonPath("$.result[0].menuNm").value("TX"))
          .andExpect(jsonPath("$.result[0].children[0].menuNm").value("거래관리"))
          .andExpect(jsonPath("$.result[0].children[0].children[0].menuId").value(3))
          .andExpect(jsonPath("$.result[0].children[0].children[0].permission.list").value(true))
          .andExpect(jsonPath("$.result[0].children[0].children[0].permission.add").value(false));
    }

    @Test
    @DisplayName("시스템관리자 - 전체 메뉴/전체 권한. 메뉴명은 사용자 설정 언어(ko)가 Accept-Language 보다 우선")
    void adminMenus_userLangFirst() throws Exception {
      String token = bearer("menu-admin", "admin", "flowlog-bff", "openid");

      mockMvc.perform(get(MENU_URL).header(HttpHeaders.AUTHORIZATION, token)
              .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.length()").value(5))
          .andExpect(jsonPath("$.result[0].children[0].menuNm").value("거래관리"))
          .andExpect(jsonPath("$.result[0].children[0].permission.del").value(true));
    }

    @Test
    @DisplayName("사용자 설정 언어 en (tx.manager) - 영문 메뉴명")
    void managerMenus_english() throws Exception {
      String token = bearer("menu-manager", "tx.manager", "flowlog-bff", "openid");

      mockMvc.perform(get(MENU_URL).header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result[0].children[0].menuNm").value("Trades"));
    }

    @Test
    @DisplayName("서비스 계정 / 미등록 사용자 - 빈 메뉴")
    void noMenus() throws Exception {
      String batch = bearer("menu-batch", "service-account-flowlog-batch", "flowlog-batch",
          "sample.list");

      mockMvc.perform(get(MENU_URL).header(HttpHeaders.AUTHORIZATION, batch))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result").isEmpty());
    }
  }

  @Nested
  @DisplayName("Forwarded 헤더 (Gateway 뒤 동작)")
  class ForwardedHeaderTest {

    private RequestPostProcessor remote(String address) {
      return request -> {
        request.setRemoteAddr(address);
        return request;
      };
    }

    @Test
    @DisplayName("신뢰 프록시(Gateway)의 X-Forwarded-* 는 반영 - redirect_uri 가 외부 URL(/cm) 기준")
    void trustedProxy_applied() throws Exception {
      String location = mockMvc.perform(get("/oauth2/authorization/keycloak")
              .with(remote("127.0.0.1"))
              .header("X-Forwarded-Host", "localhost:9909")
              .header("X-Forwarded-Proto", "http")
              .header("X-Forwarded-Prefix", "/cm"))
          .andExpect(status().is3xxRedirection())
          .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

      assertThat(location).contains("redirect_uri=http://localhost:9909/cm/login/oauth2/code");
    }

    @Test
    @DisplayName("신뢰하지 않는 호출자의 X-Forwarded-Host 위조는 무시 - redirect_uri 가 조작되지 않음")
    void untrustedClient_ignored() throws Exception {
      String location = mockMvc.perform(get("/oauth2/authorization/keycloak")
              .with(remote("10.20.30.40"))
              .header("X-Forwarded-Host", "evil.example")
              .header("X-Forwarded-Prefix", "/evil")
              .header("Forwarded", "host=evil.example;proto=https"))
          .andExpect(status().is3xxRedirection())
          .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

      assertThat(location).doesNotContain("evil").contains("redirect_uri=http://localhost");
    }
  }
}
