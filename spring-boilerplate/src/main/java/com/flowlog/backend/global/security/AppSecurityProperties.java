package com.flowlog.backend.global.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 보안 설정값. ({@code app.security.*})
 *
 * <p>Bearer JWT(Client Credentials) 검증은 Spring Boot 표준 속성
 * {@code spring.security.oauth2.resourceserver.jwt.*} 를 사용한다.
 *
 * @param oauth2 OAuth2 BFF 로그인 설정
 * @param saml SAML2 설정
 * @param serviceAccountClients Client Credentials 를 사용하는 서비스 계정 client ID 목록.
 *     토큰의 {@code azp} 가 이 목록에 있고 사용자명이 {@code service-account-{azp}} 일 때만
 *     서비스 계정으로 판단한다. (사용자명 접두사만으로 판단하지 않음)
 * @param trustedProxies X-Forwarded-* / Forwarded 헤더를 신뢰할 직전 호출자 IP 정규식 (Gateway)
 * @param authorityCacheTtl DB 권한 조회 결과 캐시 시간
 * @param requireHttps true 면 기동 시 IdP URL·쿠키 Secure·시크릿 기본값 여부를 검사해 실패시킨다. (prod)
 * @param session 세션 저장 설정
 * @author flowlog
 * @since 2026-11-05
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
    OAuth2 oauth2,
    Saml saml,
    @DefaultValue List<String> serviceAccountClients,
    @DefaultValue("127\\.0\\.0\\.1|0:0:0:0:0:0:0:1|::1") String trustedProxies,
    @DefaultValue("60s") Duration authorityCacheTtl,
    @DefaultValue("false") boolean requireHttps,
    Session session) {

  /**
   * OAuth2 BFF(Backend For Frontend) 로그인 설정.
   *
   * <p>서버가 confidential client 로 Authorization Code(+PKCE) 흐름을 수행하고,
   * 발급된 Access/Refresh Token 은 서버 세션(Redis)에만 저장한다. 브라우저는 세션 쿠키만 가진다.
   *
   * @param registrationId 등록 ID (로그인 시작: /oauth2/authorization/{registrationId})
   * @param clientId Keycloak client ID
   * @param clientSecret Keycloak client secret (환경변수로 주입)
   * @param scopes 요청 scope (Keycloak 이 사용자 역할에 따라 허용된 것만 발급)
   * @param successUrl 로그인 성공 후 이동 URL
   * @param refreshSkew 만료 이 시간 전부터 refresh token 으로 갱신
   */
  public record OAuth2(
      String registrationId, String clientId, String clientSecret, List<String> scopes,
      String successUrl, Duration refreshSkew) {
  }

  /**
   * SAML2 Service Provider 설정.
   *
   * @param registrationId 등록 ID (URL 에 사용: /saml2/authenticate/{registrationId})
   * @param entityId SP entityId (비어 있으면 {baseUrl}/saml2/service-provider-metadata/{registrationId})
   * @param metadataUri IdP(Keycloak) SAML 메타데이터 URL - 최초 SAML 요청 시 조회 후 캐시
   * @param successUrl SAML 로그인 성공 후 이동 URL
   * @param maxAuthAge SAML 로그인 유효 시간. SAML 은 토큰 갱신이 없어 IdP 의 역할 변경이
   *     재로그인 전까지 반영되지 않으므로, 이 시간이 지나면 세션을 폐기하고 재로그인시킨다.
   */
  public record Saml(
      String registrationId, String entityId, String metadataUri, String successUrl,
      @DefaultValue("1h") Duration maxAuthAge) {
  }

  /**
   * Redis 세션 저장 설정.
   *
   * @param encryptionKey 세션 값 암호화 키 (AES-256, Base64 32바이트). Redis 에 저장되는 세션
   *     (토큰 포함)을 암호화·무결성 검증한다.
   */
  public record Session(String encryptionKey) {
  }
}
