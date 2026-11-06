package com.flowlog.backend.global.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * [OAuth2] Keycloak Access Token(JWT) → 인증 객체 변환.
 *
 * <ul>
 *   <li>인증 이름 : {@code preferred_username} (표시/감사용, 없으면 {@code sub})</li>
 *   <li>권한 매핑 기준 : {@code sub} (Keycloak user id, 변경 불가) - {@link AuthorityResolver}</li>
 *   <li>서비스 계정 : {@code azp} 가 {@code app.security.service-account-clients} 에 있고
 *       사용자명이 {@code service-account-{azp}} 인 경우만. 토큰 scope 만 사용한다.</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  /** Keycloak 사용자명 claim. */
  public static final String USERNAME_CLAIM = "preferred_username";

  /** 토큰을 발급받은 client claim. */
  public static final String CLIENT_ID_CLAIM = "azp";

  /** Keycloak 서비스 계정(Client Credentials) 사용자명 접두사. */
  public static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

  private final AuthorityResolver authorityResolver;
  private final AppSecurityProperties properties;

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String username = StringUtils.defaultIfBlank(jwt.getClaimAsString(USERNAME_CLAIM),
        jwt.getSubject());
    List<String> scopes = scopesOf(jwt);
    Set<GrantedAuthority> authorities = isServiceAccount(jwt)
        ? authorityResolver.forServiceAccount(scopes)
        : authorityResolver.forUser(jwt.getSubject(), scopes);
    return new JwtAuthenticationToken(jwt, authorities, username);
  }

  /**
   * Client Credentials 서비스 계정 토큰 여부.
   *
   * @param jwt 토큰
   * @return 허용된 서비스 계정 client 가 발급받은 토큰이면 true
   */
  public boolean isServiceAccount(Jwt jwt) {
    String clientId = jwt.getClaimAsString(CLIENT_ID_CLAIM);
    return StringUtils.isNotBlank(clientId)
        && properties.serviceAccountClients().contains(clientId)
        && StringUtils.equals(jwt.getClaimAsString(USERNAME_CLAIM),
            SERVICE_ACCOUNT_PREFIX + clientId);
  }

  private static List<String> scopesOf(Jwt jwt) {
    String scope = jwt.getClaimAsString("scope");
    return StringUtils.isBlank(scope) ? List.of()
        : Arrays.stream(StringUtils.split(scope, ' ')).distinct().toList();
  }
}
