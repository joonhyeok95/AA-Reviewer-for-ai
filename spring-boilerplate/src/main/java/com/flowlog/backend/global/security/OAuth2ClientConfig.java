package com.flowlog.backend.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * OAuth2 BFF 로그인 구성.
 *
 * <ul>
 *   <li>{@link ClientRegistrationRepository} : Keycloak 엔드포인트를 issuer 기준으로 직접 구성한다.
 *       (issuer-uri discovery 는 기동 시 Keycloak 을 호출하므로 사용하지 않음 → Keycloak 없이도 기동,
 *       ID Token 의 iss 검증은 issuerUri 로 그대로 수행)</li>
 *   <li>{@link OAuth2AuthorizedClientRepository} : <b>HttpSession</b> 기반 → Spring Session 에 의해
 *       Access/Refresh Token 이 <b>Redis 세션</b>에 저장된다. (기본값인 인메모리 저장소 사용 안 함)</li>
 *   <li>{@link OAuth2AuthorizedClientManager} : 요청마다 세션의 토큰을 꺼내고, 만료가 가까우면
 *       refresh token 으로 갱신해 세션에 다시 저장한다.</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
public class OAuth2ClientConfig {

  /**
   * Keycloak BFF client 등록 정보.
   *
   * @param properties 보안 설정
   * @param issuerUri Keycloak realm issuer (Bearer JWT 검증과 동일)
   * @return ClientRegistrationRepository
   */
  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(
      AppSecurityProperties properties,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
    AppSecurityProperties.OAuth2 oauth2 = properties.oauth2();
    String oidc = issuerUri + "/protocol/openid-connect";
    ClientRegistration registration = ClientRegistration
        .withRegistrationId(oauth2.registrationId())
        .clientName("Keycloak")
        .clientId(oauth2.clientId())
        .clientSecret(oauth2.clientSecret())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope(oauth2.scopes())
        .issuerUri(issuerUri)
        .authorizationUri(oidc + "/auth")
        .tokenUri(oidc + "/token")
        .jwkSetUri(oidc + "/certs")
        .userInfoUri(oidc + "/userinfo")
        .userNameAttributeName("preferred_username")
        .build();
    return new InMemoryClientRegistrationRepository(registration);
  }

  /**
   * 토큰 저장소 : HttpSession (→ Spring Session Redis).
   *
   * @return OAuth2AuthorizedClientRepository
   */
  @Bean
  public OAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
  }

  /**
   * 세션 토큰 조회/갱신 관리자.
   *
   * @param registrations Client 등록 정보
   * @param authorizedClients 토큰 저장소 (세션)
   * @param properties 보안 설정
   * @return OAuth2AuthorizedClientManager
   */
  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository authorizedClients,
      AppSecurityProperties properties) {
    DefaultOAuth2AuthorizedClientManager manager =
        new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
        .authorizationCode()
        .refreshToken(refresh -> refresh.clockSkew(properties.oauth2().refreshSkew()))
        .build());
    return manager;
  }
}
