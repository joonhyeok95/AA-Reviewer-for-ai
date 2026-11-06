package com.flowlog.backend.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.CachingRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * 인증/인가 설정. (Keycloak 연동)
 *
 * <table>
 *   <caption>인증 방식</caption>
 *   <tr><th>방식</th><th>흐름</th><th>클라이언트가 가진 것</th></tr>
 *   <tr><td>OAuth2 BFF (브라우저)</td>
 *       <td>{@code /oauth2/authorization/keycloak} → Keycloak 로그인 → 서버가 code 를 토큰으로 교환
 *           (confidential client + PKCE) → 토큰은 세션(Redis)에 저장</td>
 *       <td><b>SESSION 쿠키만</b> (토큰 노출 없음)</td></tr>
 *   <tr><td>SAML2 (브라우저)</td>
 *       <td>{@code /saml2/authenticate/keycloak} → Keycloak 로그인 → ACS → 세션(Redis)</td>
 *       <td>SESSION 쿠키만</td></tr>
 *   <tr><td>OAuth2 Client Credentials (서버 간)</td>
 *       <td>Keycloak 에서 토큰 발급 → {@code Authorization: Bearer} → JWT 검증 (Stateless)</td>
 *       <td>자기 토큰</td></tr>
 * </table>
 *
 * <p><b>인가는 scope 기준</b>으로 통일한다. ({@code SCOPE_{리소스}.{list|add|edit|del}})
 * <ul>
 *   <li>OAuth2 BFF : 요청마다 세션의 Access Token scope ({@link SessionTokenAuthorizationFilter})</li>
 *   <li>Bearer JWT : 토큰 scope</li>
 *   <li>SAML2 : Assertion 의 Role 속성 (Keycloak 동일 이름 역할)</li>
 * </ul>
 * Keycloak 은 사용자 역할에 매핑된 scope 만 발급하므로, 같은 scope 를 요청해도 사용자마다 다르게 받는다.
 * 최종 권한 = IdP scope ∩ DB 업무 권한 ({@link AuthorityResolver}).
 * 시스템관리자({@code ROLE_SYS_ADMIN}, DB 권한)는 모든 API 허용. 규칙이 없는 경로는 거부한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * 인증 없이 접근 가능한 경로.
   * (actuator 는 별도 관리 포트(기본 127.0.0.1:9191)에서만 제공되며 서비스 포트/Gateway 로는 노출되지 않는다)
   */
  private static final String[] PUBLIC_PATHS = {
      "/actuator/health", "/actuator/info", "/actuator/prometheus",
      "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**", "/error"
  };

  /** 인증 사용자 공통 API (권한 scope 와 무관하게 로그인만 필요). */
  private static final String[] AUTHENTICATED_GET_PATHS = {
      "/api/v1/auth/me", "/api/v1/menus/me", "/api/v1/langs", "/api/v1/codes/*",
      "/api/v1/org/tree"
  };

  /** 공지사항 API. */
  private static final String NOTICE_API = "/api/v1/notices/**";

  /** 시스템관리자 (모든 API 허용). */
  private static final String SYS_ADMIN = "ROLE_SYS_ADMIN";

  /** 샘플 API 경로. */
  private static final String SAMPLE_API = "/api/v1/samples/**";

  /** Bearer 토큰 요청 (Stateless - CSRF 대상 아님). */
  private static final RequestMatcher BEARER_TOKEN_REQUEST = SecurityConfig::hasBearerToken;

  private final AppSecurityProperties properties;
  private final SecurityErrorHandler securityErrorHandler;
  private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;
  private final SamlRoleAuthoritiesConverter samlRoleAuthoritiesConverter;
  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final ObjectProvider<SessionRepository<? extends Session>> sessionRepository;
  private final AuthorityResolver authorityResolver;
  private final AuthAuditLogger auditLogger;

  /**
   * Security Filter Chain.
   *
   * @param http HttpSecurity
   * @return SecurityFilterChain
   * @throws Exception 설정 오류
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // [인가] API 권한 = scope (시스템관리자는 전체 허용).
        // 규칙이 없는 경로는 기본 거부(denyAll) - 새 API 는 반드시 규칙을 추가해야 호출 가능
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(PUBLIC_PATHS).permitAll()
            .requestMatchers(HttpMethod.GET, AUTHENTICATED_GET_PATHS).authenticated()
            .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/lang").authenticated()
            // 공지사항 : 관리 기능(수정 화면, 메일 이력, 첨부 삭제)은 notice.edit
            .requestMatchers(HttpMethod.GET, "/api/v1/notices/*/edit",
                "/api/v1/notices/*/mail-histories")
            .hasAnyAuthority("SCOPE_notice.edit", SYS_ADMIN)
            .requestMatchers(HttpMethod.POST, "/api/v1/notices/*/files")
            .hasAnyAuthority("SCOPE_notice.add", "SCOPE_notice.edit", SYS_ADMIN)
            .requestMatchers(HttpMethod.DELETE, "/api/v1/notices/*/files/*")
            .hasAnyAuthority("SCOPE_notice.edit", SYS_ADMIN)
            .requestMatchers(HttpMethod.GET, NOTICE_API)
            .hasAnyAuthority("SCOPE_notice.list", SYS_ADMIN)
            .requestMatchers(HttpMethod.POST, NOTICE_API)
            .hasAnyAuthority("SCOPE_notice.add", SYS_ADMIN)
            .requestMatchers(HttpMethod.PUT, NOTICE_API)
            .hasAnyAuthority("SCOPE_notice.edit", SYS_ADMIN)
            .requestMatchers(HttpMethod.DELETE, NOTICE_API)
            .hasAnyAuthority("SCOPE_notice.del", SYS_ADMIN)
            .requestMatchers(HttpMethod.GET, SAMPLE_API)
            .hasAnyAuthority("SCOPE_sample.list", SYS_ADMIN)
            .requestMatchers(HttpMethod.POST, SAMPLE_API)
            .hasAnyAuthority("SCOPE_sample.add", SYS_ADMIN)
            .requestMatchers(HttpMethod.PUT, SAMPLE_API)
            .hasAnyAuthority("SCOPE_sample.edit", SYS_ADMIN)
            .requestMatchers(HttpMethod.PATCH, SAMPLE_API)
            .hasAnyAuthority("SCOPE_sample.edit", SYS_ADMIN)
            .requestMatchers(HttpMethod.DELETE, SAMPLE_API)
            .hasAnyAuthority("SCOPE_sample.del", SYS_ADMIN)
            .anyRequest().denyAll())
        // 로그인 시작 시 화면 복귀 경로(returnTo) 저장 - 같은 호스트 절대 경로만 (OAuth2 / SAML2 공통)
        .addFilterBefore(new LoginReturnUrlFilter(), OAuth2AuthorizationRequestRedirectFilter.class)
        // [OAuth2 BFF] 브라우저 로그인 - 토큰은 세션(Redis)에만 보관
        .oauth2Login(login -> login
            .authorizationEndpoint(endpoint -> endpoint
                .authorizationRequestResolver(pkceAuthorizationRequestResolver()))
            .successHandler(loginSuccessHandler(properties.oauth2().successUrl(), "OAUTH2_SESSION"))
            .failureHandler(securityErrorHandler))
        // [OAuth2 BFF] 요청마다 세션 토큰의 scope 로 권한 재계산 (만료 임박 시 refresh)
        .addFilterBefore(new SessionTokenAuthorizationFilter(
            authorizedClientManager, authorizedClientRepository, sessionRepository,
            authorityResolver, samlRoleAuthoritiesConverter, properties, securityErrorHandler),
            AuthorizationFilter.class)
        // [Client Credentials] Bearer JWT 검증 (issuer-uri / audiences 는 application.yml)
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            .authenticationEntryPoint(securityErrorHandler)
            .accessDeniedHandler(securityErrorHandler))
        // [SAML2] SP 로그인 + SP 메타데이터 (/saml2/metadata/keycloak)
        .saml2Login(saml2 -> saml2
            .authenticationManager(new ProviderManager(samlAuthenticationProvider()))
            .successHandler(loginSuccessHandler(properties.saml().successUrl(), "SAML2"))
            .failureHandler(securityErrorHandler))
        .saml2Metadata(Customizer.withDefaults())
        // 로그아웃 : 세션(Redis) 폐기 + Keycloak SSO 세션 종료(서버 간 호출)
        .logout(logout -> logout
            .addLogoutHandler((request, response, authentication) -> auditLogger.log(
                AuthAuditLogger.Event.LOGOUT,
                authentication == null ? null : authentication.getName(), null, request, null))
            .addLogoutHandler(new KeycloakLogoutHandler(authorizedClientRepository))
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
        // 미인증 요청을 세션에 저장하지 않는다 (로그인 후 이동은 returnTo / success-url).
        // 저장하면 익명 요청마다 Redis 세션이 생겨, Gateway Rate Limit(실존 세션 = 세션 버킷)을 우회할 수 있다.
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        // API 이므로 로그인 페이지로 redirect 하지 않고 표준 응답(401/403) 반환
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(securityErrorHandler)
            .accessDeniedHandler(securityErrorHandler))
        // 세션 요청은 XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더 방식 CSRF 검증
        //  - 쿠키 경로 "/" : Gateway 뒤에서는 context path 가 /cm 이 되는데, 화면(SPA)은 / 에서 서비스되므로
        //    경로를 / 로 고정해야 화면 스크립트가 쿠키를 읽어 헤더로 보낼 수 있다.
        .csrf(csrf -> csrf
            .spa()
            .csrfTokenRepository(csrfTokenRepository())
            .ignoringRequestMatchers(BEARER_TOKEN_REQUEST)
            .ignoringRequestMatchers("/h2-console/**"))
        // 화면이 첫 변경 요청 전에 XSRF-TOKEN 쿠키를 갖도록 매 요청 발급 (없을 때만 Set-Cookie)
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
    return http.build();
  }

  /**
   * 브라우저 로그인 성공 : 로그인 시각 기록(SAML 유효시간 계산) + 감사 로그 + 성공 URL 로 이동.
   * (Spring Security 가 로그인 시 세션ID 를 교체하므로 세션 고정 공격 방지)
   */
  private AuthenticationSuccessHandler loginSuccessHandler(String successUrl, String authType) {
    SimpleUrlAuthenticationSuccessHandler target =
        new SimpleUrlAuthenticationSuccessHandler(successUrl);
    target.setAlwaysUseDefaultTargetUrl(true);
    return (request, response, authentication) -> {
      HttpSession session = request.getSession();
      session.setAttribute(
          SessionTokenAuthorizationFilter.AUTH_TIME_ATTRIBUTE, System.currentTimeMillis());
      Object returnTo = session.getAttribute(LoginReturnUrlFilter.RETURN_URL_ATTRIBUTE);
      session.removeAttribute(LoginReturnUrlFilter.RETURN_URL_ATTRIBUTE);
      auditLogger.log(AuthAuditLogger.Event.LOGIN_SUCCESS, authentication.getName(), authType,
          request, null);
      if (returnTo instanceof String url && LoginReturnUrlFilter.isSafe(url)) {
        // 화면 경로(호스트 기준) - Gateway 뒤에서는 X-Forwarded-Host 기준 절대 URL 로 변환된다
        response.sendRedirect(url);
      } else {
        target.onAuthenticationSuccess(request, response, authentication);
      }
    };
  }

  private static CookieCsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookiePath("/");
    return repository;
  }

  /** confidential client 이지만 PKCE 도 함께 사용한다. (인가코드 탈취 대비) */
  private OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver() {
    DefaultOAuth2AuthorizationRequestResolver resolver =
        new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    return resolver;
  }

  /**
   * [SAML2] IdP(Keycloak) 등록 정보.
   *
   * <p>IdP 메타데이터는 최초 SAML 요청 시 조회 후 캐시한다.
   * (Keycloak 이 떠 있지 않아도 애플리케이션은 기동되고, OAuth2 인증은 정상 동작)
   *
   * @return RelyingPartyRegistrationRepository
   */
  @Bean
  public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
    AppSecurityProperties.Saml saml = properties.saml();
    return new CachingRelyingPartyRegistrationRepository(() ->
        new InMemoryRelyingPartyRegistrationRepository(
            RelyingPartyRegistrations.fromMetadataLocation(saml.metadataUri())
                .registrationId(saml.registrationId())
                // SP entityId 는 접속 경로(Gateway/직접)와 무관하게 고정 (Keycloak SAML clientId 와 일치)
                .entityId(StringUtils.defaultIfBlank(saml.entityId(),
                    "{baseUrl}/saml2/service-provider-metadata/{registrationId}"))
                // Keycloak realm 메타데이터는 WantAuthnRequestsSigned=true 로 공개되지만,
                // SAML client 설정(saml.client.signature=false)으로 요청 서명을 요구하지 않는다.
                // 운영에서 요청 서명/SLO 가 필요하면 SP 서명키를 등록하고 이 값을 제거한다.
                .assertingPartyMetadata(party -> party.wantAuthnRequestsSigned(false))
                .build()));
  }

  private OpenSaml5AuthenticationProvider samlAuthenticationProvider() {
    OpenSaml5AuthenticationProvider.ResponseAuthenticationConverter responseConverter =
        new OpenSaml5AuthenticationProvider.ResponseAuthenticationConverter();
    responseConverter.setGrantedAuthoritiesConverter(samlRoleAuthoritiesConverter);
    OpenSaml5AuthenticationProvider provider = new OpenSaml5AuthenticationProvider();
    provider.setResponseAuthenticationConverter(responseConverter);
    return provider;
  }

  private static boolean hasBearerToken(HttpServletRequest request) {
    return StringUtils.startsWithIgnoreCase(
        request.getHeader(HttpHeaders.AUTHORIZATION), "Bearer ");
  }
}
