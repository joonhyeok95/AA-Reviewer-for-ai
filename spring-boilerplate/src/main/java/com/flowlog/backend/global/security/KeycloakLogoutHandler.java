package com.flowlog.backend.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * [OAuth2 BFF] 로그아웃 시 Keycloak SSO 세션도 종료한다. (서버 간 호출)
 *
 * <p>세션에 보관된 refresh token 으로 Keycloak logout 엔드포인트를 호출한다.
 * 브라우저 redirect 가 없으므로 토큰이 클라이언트에 노출되지 않는다.
 * 실패해도 애플리케이션 세션 폐기는 계속 진행한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
public class KeycloakLogoutHandler implements LogoutHandler {

  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final RestClient restClient;

  /**
   * 생성자.
   *
   * @param authorizedClientRepository 세션 토큰 저장소
   */
  public KeycloakLogoutHandler(OAuth2AuthorizedClientRepository authorizedClientRepository) {
    this.authorizedClientRepository = authorizedClientRepository;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(3));
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  public void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken oauth2) {
      OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
          oauth2.getAuthorizedClientRegistrationId(), oauth2, request);
      if (client != null && client.getRefreshToken() != null) {
        endKeycloakSession(client);
      }
    }
  }

  private void endKeycloakSession(OAuth2AuthorizedClient client) {
    ClientRegistration registration = client.getClientRegistration();
    String logoutUri = registration.getProviderDetails().getIssuerUri()
        + "/protocol/openid-connect/logout";
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", registration.getClientId());
    form.add("client_secret", registration.getClientSecret());
    form.add("refresh_token", client.getRefreshToken().getTokenValue());
    try {
      restClient.post().uri(logoutUri)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
      log.info("[Logout] Keycloak SSO 세션 종료 user={}", client.getPrincipalName());
    } catch (RestClientException e) {
      log.warn("[Logout] Keycloak SSO 세션 종료 실패 user={} ({})", client.getPrincipalName(),
          e.getMessage());
    }
  }
}
